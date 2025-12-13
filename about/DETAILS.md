
## 🧩 `@Widener`

The @Widener annotation marks classes or interfaces whose target classes should be made accessible across mod loaders.

---

## 💡 Usage

The Crafter Gradle plugin scans `@Widener` annotations during sync or build and generates access configuration files.

To create an accessor:

1. Annotate the future accessor class with @Widener, specifying the target class.
2. Run a Gradle sync to generate the AW/AT.
3. Reference the target class in the @Mixin annotation on the accessor class.

Example:

```java
import io.github.diskria.crafter.annotations.Widener;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.world.item.ItemStack;

@Widener("net.minecraft.world.item.ItemStack")
@Mixin(ItemStack.class)
public interface ItemStackAccessor {
    // Accessor methods here
}
```

---

## 📄 Generated Output

For Fabric / Quilt (Access Widener format)
```
accessible class net/minecraft/world/item/ItemStack
```

For Forge / NeoForge (Access Transformer format)
```
public-f net/minecraft/world/item/ItemStack
```
