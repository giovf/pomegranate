package com.gankdat.pomegranate.data.model

/** How pulled files are laid out under `<root>/Pomegranate/YYYY/YYYY-MM/`. */
public enum class FolderStrategy {
    /** `YYYY/YYYY-MM/<Origin>/`, with unknown origins falling back to the month folder. */
    BY_ORIGIN,

    /** `YYYY/YYYY-MM/` only; origin is ignored. */
    BY_MONTH_ONLY,
}
