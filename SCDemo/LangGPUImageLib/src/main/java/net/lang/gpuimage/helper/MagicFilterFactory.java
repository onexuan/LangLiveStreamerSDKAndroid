package net.lang.gpuimage.helper;

import android.content.Context;

import net.lang.gpuimage.filter.advanced.MagicAmaroFilter;
import net.lang.gpuimage.filter.advanced.MagicAntiqueFilter;
import net.lang.gpuimage.filter.advanced.MagicBlackCatFilter;
import net.lang.gpuimage.filter.advanced.MagicBrannanFilter;
import net.lang.gpuimage.filter.advanced.MagicBrooklynFilter;
import net.lang.gpuimage.filter.advanced.MagicCalmFilter;
import net.lang.gpuimage.filter.advanced.MagicCoolFilter;
import net.lang.gpuimage.filter.advanced.MagicCrayonFilter;
import net.lang.gpuimage.filter.advanced.MagicEarlyBirdFilter;
import net.lang.gpuimage.filter.advanced.MagicEmeraldFilter;
import net.lang.gpuimage.filter.advanced.MagicEvergreenFilter;
import net.lang.gpuimage.filter.advanced.MagicFairytaleFilter;
import net.lang.gpuimage.filter.advanced.MagicFreudFilter;
import net.lang.gpuimage.filter.advanced.MagicHealthyFilter;
import net.lang.gpuimage.filter.advanced.MagicHefeFilter;
import net.lang.gpuimage.filter.advanced.MagicHudsonFilter;
import net.lang.gpuimage.filter.advanced.MagicImageAdjustFilter;
import net.lang.gpuimage.filter.advanced.MagicInkwellFilter;
import net.lang.gpuimage.filter.advanced.MagicKevinFilter;
import net.lang.gpuimage.filter.advanced.MagicLatteFilter;
import net.lang.gpuimage.filter.advanced.MagicLomoFilter;
import net.lang.gpuimage.filter.advanced.MagicN1977Filter;
import net.lang.gpuimage.filter.advanced.MagicNashvilleFilter;
import net.lang.gpuimage.filter.advanced.MagicNostalgiaFilter;
import net.lang.gpuimage.filter.advanced.MagicPixarFilter;
import net.lang.gpuimage.filter.advanced.MagicRiseFilter;
import net.lang.gpuimage.filter.advanced.MagicRomanceFilter;
import net.lang.gpuimage.filter.advanced.MagicSakuraFilter;
import net.lang.gpuimage.filter.advanced.MagicSierraFilter;
import net.lang.gpuimage.filter.advanced.MagicSketchFilter;
import net.lang.gpuimage.filter.advanced.MagicSkinWhitenFilter;
import net.lang.gpuimage.filter.advanced.MagicSunriseFilter;
import net.lang.gpuimage.filter.advanced.MagicSunsetFilter;
import net.lang.gpuimage.filter.advanced.MagicSutroFilter;
import net.lang.gpuimage.filter.advanced.MagicSweetsFilter;
import net.lang.gpuimage.filter.advanced.MagicTenderFilter;
import net.lang.gpuimage.filter.advanced.MagicToasterFilter;
import net.lang.gpuimage.filter.advanced.MagicValenciaFilter;
import net.lang.gpuimage.filter.advanced.MagicWaldenFilter;
import net.lang.gpuimage.filter.advanced.MagicWarmFilter;
import net.lang.gpuimage.filter.advanced.MagicWhiteCatFilter;
import net.lang.gpuimage.filter.advanced.MagicXproIIFilter;
import net.lang.gpuimage.filter.advanced.beauty.MagicBeautySmoothingFilter;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageBrightnessFilter;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageContrastFilter;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageExposureFilter;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageFilter;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageHueFilter;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageSaturationFilter;
import net.lang.gpuimage.filter.base.gpuimage.GPUImageSharpenFilter;

public class MagicFilterFactory{
	
	private static MagicFilterType filterType = MagicFilterType.NONE;
	
	public static GPUImageFilter initFilters(MagicFilterType type, Context context){
		filterType = type;
		switch (type) {
		case WHITECAT:
			return new MagicWhiteCatFilter(context);
		case BLACKCAT:
			return new MagicBlackCatFilter(context);
		case BEAUTY:
			return new MagicBeautySmoothingFilter(context);
		case SKINWHITEN:
			return new MagicSkinWhitenFilter(context);
		case ROMANCE:
			return new MagicRomanceFilter(context);
		case SAKURA:
			return new MagicSakuraFilter(context);
		case AMARO:
			return new MagicAmaroFilter(context);
		case WALDEN:
			return new MagicWaldenFilter(context);
		case ANTIQUE:
			return new MagicAntiqueFilter(context);
		case CALM:
			return new MagicCalmFilter(context);
		case BRANNAN:
			return new MagicBrannanFilter(context);
		case BROOKLYN:
			return new MagicBrooklynFilter(context);
		case EARLYBIRD:
			return new MagicEarlyBirdFilter(context);
		case FREUD:
			return new MagicFreudFilter(context);
		case HEFE:
			return new MagicHefeFilter(context);
		case HUDSON:
			return new MagicHudsonFilter(context);
		case INKWELL:
			return new MagicInkwellFilter(context);
		case KEVIN:
			return new MagicKevinFilter(context);
		case LOMO:
			return new MagicLomoFilter(context);
		case N1977:
			return new MagicN1977Filter(context);
		case NASHVILLE:
			return new MagicNashvilleFilter(context);
		case PIXAR:
			return new MagicPixarFilter(context);
		case RISE:
			return new MagicRiseFilter(context);
		case SIERRA:
			return new MagicSierraFilter(context);
		case SUTRO:
			return new MagicSutroFilter(context);
		case TOASTER2:
			return new MagicToasterFilter(context);
		case VALENCIA:
			return new MagicValenciaFilter(context);
		case XPROII:
			return new MagicXproIIFilter(context);
		case EVERGREEN:
			return new MagicEvergreenFilter(context);
		case HEALTHY:
			return new MagicHealthyFilter(context);
		case COOL:
			return new MagicCoolFilter(context);
		case EMERALD:
			return new MagicEmeraldFilter(context);
		case LATTE:
			return new MagicLatteFilter(context);
		case WARM:
			return new MagicWarmFilter(context);
		case TENDER:
			return new MagicTenderFilter(context);
		case SWEETS:
			return new MagicSweetsFilter(context);
		case NOSTALGIA:
			return new MagicNostalgiaFilter(context);
		case FAIRYTALE:
			return new MagicFairytaleFilter(context);
		case SUNRISE:
			return new MagicSunriseFilter(context);
		case SUNSET:
			return new MagicSunsetFilter(context);
		case CRAYON:
			return new MagicCrayonFilter(context);
		case SKETCH:
			return new MagicSketchFilter(context);
		//image adjust
		case BRIGHTNESS:
			return new GPUImageBrightnessFilter();
		case CONTRAST:
			return new GPUImageContrastFilter();
		case EXPOSURE:
			return new GPUImageExposureFilter();
		case HUE:
			return new GPUImageHueFilter();
		case SATURATION:
			return new GPUImageSaturationFilter();
		case SHARPEN:
			return new GPUImageSharpenFilter();
		case IMAGE_ADJUST:
			return new MagicImageAdjustFilter();
		default:
			return null;
		}
	}
	
	public MagicFilterType getCurrentFilterType(){
		return filterType;
	}
}
