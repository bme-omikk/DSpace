package org.dspace.app.rest.statistics;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.storage.bitstore.BitStoreService;
import org.dspace.storage.bitstore.BitstreamStorageServiceImpl;
import org.dspace.storage.bitstore.DSBitStoreService;
import org.dspace.storage.bitstore.factory.StorageServiceFactory;
import org.dspace.storage.bitstore.service.BitstreamStorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Custom endpoint that serves PDF bitstreams via Apache mod_xsendfile so that
 * Apache – not Spring Boot – handles the actual byte transfer and therefore
 * supports proper HTTP 206 Partial Content / Range requests for very large
 * files (hundreds of MB to several GB).
 *
 * The endpoint is intentionally narrow: it only serves bitstreams whose
 * owning item carries the metadata field  local.viewpdf.enabled = "true".
 * All other bitstream delivery (download, IIIF, etc.) remains untouched.
 *
 * Apache must be configured with:
 *   XSendFile On
 *   XSendFilePath /home/dspace/repozitorium/assetstore
 */
@RestController
@RequestMapping("/api/xpdf")
public class XpdfController {

    private static final Logger log = LoggerFactory.getLogger(XpdfController.class);

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("===== XpdfController registered successfully =====");
    }

    // These match the constants in BaseBitStoreService exactly.
    // digitsPerLevel=2, directoryLevels=3 is the DSpace default scatter layout.
    private static final int DIGITS_PER_LEVEL = 2;
    private static final int DIRECTORY_LEVELS = 3;
    private static final String REGISTERED_FLAG = "-R";

    // -----------------------------------------------------------------------
    // Service wiring – Use DSpaceServicesFactory like CustomStatsController
    // because @Autowired may not work in the additions module context.
    // -----------------------------------------------------------------------
    private BitstreamService getBitstreamService() {
        return ContentServiceFactory.getInstance().getBitstreamService();
    }

    private ItemService getItemService() {
        return ContentServiceFactory.getInstance().getItemService();
    }

    private BitstreamStorageService getBitstreamStorageService() {
        return StorageServiceFactory.getInstance().getBitstreamStorageService();
    }

    // -----------------------------------------------------------------------

    @GetMapping("/stream")
    @PreAuthorize("hasPermission(#uuid, 'BITSTREAM', 'READ')")
    public void streamPdf(@RequestParam UUID uuid,
                                       HttpServletRequest request,
                                       HttpServletResponse response)
            throws Exception {

        Context context = ContextUtil.obtainContext(request);

        log.info("XpdfController: request received for bitstream={}", uuid);

        // 1. Resolve the bitstream
        Bitstream bitstream = getBitstreamService().find(context, uuid);
        if (bitstream == null || bitstream.isDeleted()) {
            log.warn("XpdfController: 404 - bitstream not found or deleted. uuid={}", uuid);
            context.abort();
            response.sendError(404); return;
        }

        // 2. Only serve PDF bitstreams through this endpoint
        String mime = bitstream.getFormat(context).getMIMEType();
        if (!"application/pdf".equalsIgnoreCase(mime)) {
            log.warn("XpdfController: 404 - not a PDF. uuid={} mime={}", uuid, mime);
            context.abort();
            response.sendError(404); return;
        }

        // 3. Walk bitstream → bundles → items to find the owning item.
        //    This is the correct, efficient way in DSpace 9.x; no full-table
        //    metadata scan needed.
        Item owningItem = null;
        outer:
        for (Bundle bundle : bitstream.getBundles()) {
            for (Item item : bundle.getItems()) {
                owningItem = item;
                break outer;
            }
        }

        if (owningItem == null) {
            log.warn("XpdfController: 404 - no owning item found. uuid={}", uuid);
            context.abort();
            response.sendError(404); return;
        }

        log.info("XpdfController: bitstream={} belongs to item={}", uuid, owningItem.getID());

        // 4. Gate: the item must carry local.viewpdf.enabled with any value
        //    ("viewer", "viewer-download", or "download")
        List<MetadataValue> mdValues = getItemService().getMetadata(
                owningItem, "local", "viewpdf", "enabled", Item.ANY);
        if (mdValues == null || mdValues.isEmpty()) {
            log.warn("XpdfController: 404 - local.viewpdf.enabled metadata missing. uuid={} item={}",
                    uuid, owningItem.getID());
            context.abort();
            response.sendError(404); return;
        }

        log.info("XpdfController: local.viewpdf.enabled='{}' for item={}",
                mdValues.get(0).getValue(), owningItem.getID());

        // 5. Resolve the physical path on disk.
        File file = resolveFile(bitstream);
        if (file == null || !file.exists()) {
            log.error("XpdfController: 404 - physical file not found. uuid={} resolvedPath={}",
                    uuid, file != null ? file.getAbsolutePath() : "null");
            context.abort();
            response.sendError(404); return;
        }

        // Capture values that require an active Hibernate session BEFORE closing context
        String filename = sanitizeFilename(bitstream.getName());
        long sizeBytes = bitstream.getSizeBytes();

        context.complete();

        // 6. Hand off to Apache via X-Sendfile.
        //    Spring returns an empty 200 body; Apache intercepts the header,
        //    replaces the response with the actual file content and sends
        //    proper 206 Partial Content responses to the browser.
        log.info("XpdfController: SUCCESS - serving via X-Sendfile. bitstream={} path={} size={}",
                uuid, file.getAbsolutePath(), sizeBytes);

        response.setHeader("X-Sendfile", file.getAbsolutePath());
        response.setHeader("Content-Type", "application/pdf");
        response.setHeader("Content-Disposition",
                "inline; filename=\"" + filename + "\"");
        response.setStatus(200);
        response.flushBuffer();
    }

    // -----------------------------------------------------------------------
    // Path resolution – replicated from DSBitStoreService.getFile() and
    // BaseBitStoreService helpers. DSBitStoreService.getFile() is protected
    // so we cannot call it directly; replicating the logic is the simplest
    // approach that requires no reflection and no extra helper classes.
    // -----------------------------------------------------------------------

    /**
     * Resolves the on-disk {@link File} for a bitstream.
     * Only works for bitstreams stored in the default filesystem assetstore
     * ({@link DSBitStoreService}). S3-backed bitstreams return {@code null}.
     */
    private File resolveFile(Bitstream bitstream) {
        if (bitstream == null) {
            return null;
        }

        try {
            // Get the concrete storage impl so we can access the store map.
            BitstreamStorageService bss = getBitstreamStorageService();
            if (!(bss instanceof BitstreamStorageServiceImpl)) {
                log.warn("Unexpected BitstreamStorageService implementation; cannot resolve local path.");
                return null;
            }
            BitstreamStorageServiceImpl storageImpl = (BitstreamStorageServiceImpl) bss;
            Map<Integer, BitStoreService> stores = storageImpl.getStores();

            BitStoreService store = stores.get(bitstream.getStoreNumber());
            if (store == null) {
                log.warn("No store found for store number {}", bitstream.getStoreNumber());
                return null;
            }
            if (!(store instanceof DSBitStoreService)) {
                log.warn("Bitstream {} is in store {} which is not a DSBitStoreService ({}); "
                        + "X-Sendfile not supported for non-filesystem stores.",
                        bitstream.getID(), bitstream.getStoreNumber(), store.getClass().getName());
                return null;
            }

            DSBitStoreService dsStore = (DSBitStoreService) store;
            File baseDir = dsStore.getBaseDir();  // public method
            if (baseDir == null) {
                log.error("DSBitStoreService baseDir is null for store {}", bitstream.getStoreNumber());
                return null;
            }

            // --- replicated logic from DSBitStoreService.getFile() ---
            String sInternalId = bitstream.getInternalId();
            String sIntermediatePath;

            if (sInternalId.startsWith(REGISTERED_FLAG)) {
                // registered bitstream: strip the flag, no intermediate path
                sInternalId = sInternalId.substring(REGISTERED_FLAG.length());
                sIntermediatePath = "";
            } else {
                // path-traversal sanity check (mirrors sanitizeIdentifier)
                if (sInternalId.contains(File.separator)) {
                    sInternalId = sInternalId.substring(sInternalId.lastIndexOf(File.separator) + 1);
                }
                sIntermediatePath = computeIntermediatePath(sInternalId);
            }

            StringBuilder bufFilename = new StringBuilder();
            bufFilename.append(baseDir.getCanonicalFile());
            bufFilename.append(File.separator);
            bufFilename.append(sIntermediatePath);
            bufFilename.append(sInternalId);

            File bitstreamFile = new File(bufFilename.toString());
            Path normalizedPath = bitstreamFile.toPath().normalize();

            // security: reject paths that escape the assetstore root
            String[] allowedRoots = DSpaceServicesFactory.getInstance()
                    .getConfigurationService()
                    .getArrayProperty("assetstore.allowed.roots", new String[]{});

            if (!normalizedPath.startsWith(baseDir.getCanonicalPath())
                    && !StringUtils.startsWithAny(normalizedPath.toString(), allowedRoots)) {
                log.error("Bitstream path outside assetstore root: bitstream={} path={} assetstore={}",
                        bitstream.getID(), normalizedPath, baseDir.getCanonicalPath());
                throw new IOException("Illegal bitstream path constructed");
            }

            return bitstreamFile;

        } catch (IOException e) {
            log.error("Failed to resolve file for bitstream {}: {}", bitstream.getID(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Replicates {@code BaseBitStoreService.getIntermediatePath()}.
     * Splits the internal ID into DIRECTORY_LEVELS subdirectory components,
     * each DIGITS_PER_LEVEL characters wide, e.g.:
     *   "12345678..." → "12/34/56/"
     */
    private String computeIntermediatePath(String internalId) {
        StringBuilder path = new StringBuilder();
        if (StringUtils.isEmpty(internalId) || internalId.length() <= DIGITS_PER_LEVEL) {
            path.append(internalId).append(File.separator);
            return path.toString();
        }

        int digits = 0;
        path.append(safeSubstring(internalId, digits, digits + DIGITS_PER_LEVEL));
        for (int i = 1; i < DIRECTORY_LEVELS && !((digits + DIGITS_PER_LEVEL) > internalId.length()); i++) {
            digits = i * DIGITS_PER_LEVEL;
            path.append(File.separator);
            path.append(safeSubstring(internalId, digits, digits + DIGITS_PER_LEVEL));
        }

        // ensure trailing separator
        if (path.lastIndexOf(File.separator) != path.length() - 1) {
            path.append(File.separator);
        }
        return path.toString();
    }

    /**
     * Replicates {@code BaseBitStoreService.extractSubstringFrom()}: takes a
     * substring but truncates endIndex to the string length if it would overflow.
     */
    private String safeSubstring(String s, int start, int end) {
        if (end > s.length()) {
            end = s.length();
        }
        return s.substring(start, end);
    }

    // -----------------------------------------------------------------------

    /**
     * Strip characters that would break a Content-Disposition header value.
     */
    private String sanitizeFilename(String name) {
        if (name == null) {
            return "document.pdf";
        }
        return name.replaceAll("[\\r\\n\"%]", "_");
    }
}
