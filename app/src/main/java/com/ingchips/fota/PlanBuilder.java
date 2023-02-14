package com.ingchips.fota;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder of update plan which is performed by `Updater`
 *
 * Usage:
 *
 * 1. use `fromPackage` to initialize a `Plan`
 * 1. use `makeFlashProcedure` to set up the `Plan`
 */
public class PlanBuilder {

    static public final int CHIP_SERIES_ING9187XX_ING9186XX = 0;
    static public final int CHIP_SERIES_ING9168XX = 1;

    static private class FlashInfo {
        long BaseAddr;
        int TotalSize;
        int PageSize;
        boolean ManualReboot;
        FlashInfo(long BaseAddr, int TotalSize, int PageSize, boolean ManualReboot) {
            this.BaseAddr = BaseAddr;
            this.TotalSize = TotalSize;
            this.PageSize = PageSize;
            this.ManualReboot = ManualReboot;
        }
    }

    static public class Plan {
        public List<UpdateItem> items = new ArrayList<>();
        public UpdateItem metaData;
        public boolean platform;
        public boolean app;
        public boolean manualReboot;
        public long entry;
        public int pageSize;
        private Plan() {}
    }

    private final static FlashInfo []FlashInfos = {
            new FlashInfo(0x4000, 512 * 1024, 8 * 1024, true),        // ING9188
            new FlashInfo(0x02000000, 512 * 1024, 4 * 1024, false)
    };

    public static boolean makeFlashProcedure(Plan plan, int chipSeries, boolean isSecure) {
        if (isSecure) {
            // TODO: implement Secure FOTA
            return false;
        }
        FlashInfo CurrentFlash = FlashInfos[chipSeries];
        plan.manualReboot = CurrentFlash.ManualReboot;
        plan.pageSize = CurrentFlash.PageSize;

        int FLASH_PAGE_SIZE = CurrentFlash.PageSize;
        long FLASH_OTA_DATA_HIGH = CurrentFlash.BaseAddr + CurrentFlash.TotalSize;

        for (UpdateItem item : plan.items) {
            int size = ((item.data.length + (FLASH_PAGE_SIZE - 1)) / FLASH_PAGE_SIZE) * FLASH_PAGE_SIZE;
            FLASH_OTA_DATA_HIGH -= size;
            item.writeAddr = FLASH_OTA_DATA_HIGH;
        }

        byte [] b = new byte [2 + 4 + plan.items.size() * 4 * 3];
        int c = 2;
        Utils.writeU32LE(b, c, plan.entry); c += 4;
        for (UpdateItem item : plan.items) {
            Utils.writeU32LE(b, c, item.writeAddr); c += 4;
            Utils.writeU32LE(b, c, item.loadAddr); c += 4;
            Utils.writeU32LE(b, c, item.data.length); c += 4;
        }

        Utils.writeU16LE(b, 0, Utils.crc(b, 2, b.length - 2));

        plan.metaData = new UpdateItem();
        plan.metaData.name = "metadata";
        plan.metaData.data = b;

        return true;
    }

    public static Plan fromPackage(UpdatePackage pack, Updater.ProductVersion devVersion) {
        Plan r = new Plan();

        r.entry = pack.getEntry();

        if (pack.platform != null) {
            r.platform = pack.version.getPlatform().compare(devVersion.getPlatform()) != 0;
            r.app = r.platform || pack.version.getApp().compare(devVersion.getApp()) > 0;
        } else {
            r.platform = false;
            r.app = true;
        }

        if (r.platform) r.items.add(pack.platform);
        if (r.app) r.items.add(pack.app);

        r.items.addAll(pack.extraBins);
        return r;
    }
}
