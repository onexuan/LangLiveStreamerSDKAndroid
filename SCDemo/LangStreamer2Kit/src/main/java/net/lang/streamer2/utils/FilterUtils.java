package net.lang.streamer2.utils;

import net.lang.gpuimage.helper.MagicFilterType;
import net.lang.streamer2.ILangCameraStreamer;

public class FilterUtils {
    public static MagicFilterType filterTypeFromFilter(ILangCameraStreamer.LangCameraFilter filter) {
        MagicFilterType filterType = MagicFilterType.NONE;
        switch (filter) {
            case LANG_FILTER_FAIRYTALE:
                filterType = MagicFilterType.FAIRYTALE;
                break;
            case LANG_FILTER_SUNRISE:
                filterType = MagicFilterType.SUNRISE;
                break;
            case LANG_FILTER_SUNSET:
                filterType = MagicFilterType.SUNSET;
                break;
            case LANG_FILTER_WHITECAT:
                filterType = MagicFilterType.WHITECAT;
                break;
            case LANG_FILTER_BLACKCAT:
                filterType = MagicFilterType.BLACKCAT;
                break;
            case LANG_FILTER_SKINWHITEN:
                filterType = MagicFilterType.SKINWHITEN;
                break;
            case LANG_FILTER_HEALTHY:
                filterType = MagicFilterType.HEALTHY;
                break;
            case LANG_FILTER_SWEETS:
                filterType = MagicFilterType.SWEETS;
                break;
            case LANG_FILTER_ROMANCE:
                filterType = MagicFilterType.ROMANCE;
                break;
            case LANG_FILTER_SAKURA:
                filterType = MagicFilterType.SAKURA;
                break;
            case LANG_FILTER_WARM:
                filterType = MagicFilterType.WARM;
                break;
            case LANG_FILTER_ANTIQUE:
                filterType = MagicFilterType.ANTIQUE;
                break;
            case LANG_FILTER_NOSTALGIA:
                filterType = MagicFilterType.NOSTALGIA;
                break;
            case LANG_FILTER_CALM:
                filterType = MagicFilterType.CALM;
                break;
            case LANG_FILTER_LATTE:
                filterType = MagicFilterType.LATTE;
                break;
            case LANG_FILTER_TENDER:
                filterType = MagicFilterType.TENDER;
                break;
            case LANG_FILTER_COOL:
                filterType = MagicFilterType.COOL;
                break;
            case LANG_FILTER_EMERALD:
                filterType = MagicFilterType.EMERALD;
                break;
            case LANG_FILTER_EVERGREEN:
                filterType = MagicFilterType.EVERGREEN;
                break;
            case LANG_FILTER_CRAYON:
                filterType = MagicFilterType.CRAYON;
                break;
            case LANG_FILTER_SKETCH:
                filterType = MagicFilterType.SKETCH;
                break;
            case LANG_FILTER_AMARO:
                filterType = MagicFilterType.AMARO;
                break;
            case LANG_FILTER_BRANNAN:
                filterType = MagicFilterType.BRANNAN;
                break;
            case LANG_FILTER_BROOKLYN:
                filterType = MagicFilterType.BROOKLYN;
                break;
            case LANG_FILTER_EARLYBIRD:
                filterType = MagicFilterType.EARLYBIRD;
                break;
            case LANG_FILTER_FREUD:
                filterType = MagicFilterType.FREUD;
                break;
            case LANG_FILTER_HEFE:
                filterType = MagicFilterType.HEFE;
                break;
            case LANG_FILTER_HUDSON:
                filterType = MagicFilterType.HUDSON;
                break;
            case LANG_FILTER_INKWELL:
                filterType = MagicFilterType.INKWELL;
                break;
            case LANG_FILTER_KEVIN:
                filterType = MagicFilterType.KEVIN;
                break;
            case LANG_FILTER_LOMO:
                filterType = MagicFilterType.LOMO;
                break;
            case LANG_FILTER_N1977:
                filterType = MagicFilterType.N1977;
                break;
            case LANG_FILTER_NASHVILLE:
                filterType = MagicFilterType.NASHVILLE;
                break;
            case LANG_FILTER_PIXAR:
                filterType = MagicFilterType.PIXAR;
                break;
            case LANG_FILTER_RISE:
                filterType = MagicFilterType.RISE;
                break;
            case LANG_FILTER_SIERRA:
                filterType = MagicFilterType.SIERRA;
                break;
            case LANG_FILTER_SUTRO:
                filterType = MagicFilterType.SUTRO;
                break;
            case LANG_FILTER_TOASTER2:
                filterType = MagicFilterType.TOASTER2;
                break;
            case LANG_FILTER_WALDEN:
                filterType = MagicFilterType.WALDEN;
                break;
            default:
                break;
        }
        return filterType;
    }
}
