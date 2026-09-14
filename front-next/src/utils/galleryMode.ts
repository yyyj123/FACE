export interface GalleryModeSignals {
  width: number
}

export const NATIVE_GALLERY_BREAKPOINT = 1023

export function shouldUseNativeGallery(signals: GalleryModeSignals) {
  return signals.width <= NATIVE_GALLERY_BREAKPOINT
}
