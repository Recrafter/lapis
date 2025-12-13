package io.github.recrafter.crafter.annotations

/**
 * Marks a class as a widener target.
 * 
 * Detected by the Crafter Gradle plugin to generate
 * access widener / transformer entries automatically.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Widener(
    /**
     * Full dot-separated class name to widen.
     * Example: "net.minecraft.world.item.ItemStack"
     */
    val value: String
)
