package org.integratedmodelling.klab.runtime.kactors.view;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class KActorsView {

    static public Set<String> layoutMetadata = null;
    static public Set<String> isoLanguages;

    /**
     * Metadata for layout control
     * <p>
     * No argument:
     * <ul>
     * <li>:right, :left, :top, :bottom</li>
     * <li>:hfill, :vfill, :fill</li>
     * <li>:disabled {!disabled for completeness}</li>
     * <li>:hidden {!hidden}</li>
     * <li>:hbox :vbox :pager :shelf :tabs [:table is the default] to specify the type of
     * arrangement in a group</li>
     * </ul>
     * <p>
     * With argument:
     * <ul>
     * <li>:cspan, :rspan (columns and rows spanned in grid)</li>
     * <li>:fg, :bg (color name for now?)</li>
     * <li>:bstyle {?HTML solid dotted}</li>
     * <li>:bwidth <n> border width (always solid for now)</li>
     * <li>:fstyle {bold|italic|strike|normal}</li>
     * <li>:fsize <n></li>
     * <li>:symbol {font awesome char code}</li>
     * <li>:class (CSS class)</li>
     * <li>:wmin, :hmin (minimum height and width)</li>
     * <li>:cols, :equal for panel grids</li>
     * </ul>
     */
    static {
        layoutMetadata = new HashSet<>();
        layoutMetadata.add("right");
        layoutMetadata.add("left");
        layoutMetadata.add("top");
        layoutMetadata.add("bottom");
        layoutMetadata.add("hfill");
        layoutMetadata.add("vfill");
        layoutMetadata.add("fill");
        layoutMetadata.add("icon");
        layoutMetadata.add("iconname");
        layoutMetadata.add("iconsize");
        layoutMetadata.add("toggle");
        layoutMetadata.add("info");
        layoutMetadata.add("disabled");
        layoutMetadata.add("hidden");
        layoutMetadata.add("hbox");
        layoutMetadata.add("vbox");
        layoutMetadata.add("inputgroup");
        layoutMetadata.add("pager");
        layoutMetadata.add("shelf");
        layoutMetadata.add("tabs");
        layoutMetadata.add("cspan");
        layoutMetadata.add("rspan");
        layoutMetadata.add("fg");
        layoutMetadata.add("bg");
        layoutMetadata.add("bwidth");
        layoutMetadata.add("checked");
        layoutMetadata.add("waiting");
        layoutMetadata.add("computing");
        layoutMetadata.add("error");
        layoutMetadata.add("done");
        layoutMetadata.add("bstyle");
        layoutMetadata.add("fstyle");
        layoutMetadata.add("fsize");
        layoutMetadata.add("symbol");
        layoutMetadata.add("class");
        layoutMetadata.add("wmin");
        layoutMetadata.add("wmax");
        layoutMetadata.add("hmin");
        layoutMetadata.add("hmax");
        layoutMetadata.add("height");
        layoutMetadata.add("width");
        layoutMetadata.add("cols");
        layoutMetadata.add("equal");
        layoutMetadata.add("collapse");
        layoutMetadata.add("remove");
        layoutMetadata.add("altfg");
        layoutMetadata.add("altbg");
        layoutMetadata.add("tooltip");
        layoutMetadata.add("ellipsis");
        layoutMetadata.add("multiple");
        layoutMetadata.add("selected");
        layoutMetadata.add("type");
        layoutMetadata.add("active");
        layoutMetadata.add("timeout");

        isoLanguages = new HashSet<>();
        for (String isoLanguage : Locale.getISOLanguages()) {
            isoLanguages.add(isoLanguage);
        }
    }

}
