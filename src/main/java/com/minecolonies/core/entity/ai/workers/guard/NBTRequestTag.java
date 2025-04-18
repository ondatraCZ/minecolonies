package com.minecolonies.core.entity.ai.workers.guard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import com.minecolonies.api.util.ItemStackUtils;

public class NBTRequestTag extends RequestTag {

    private static final String NBT_TAG = "Tag";
    private static final String NBT_RESULT = "Result";
    private static final String NBT_COUNT = "Count";
    private static final String NBT_MINCOUNT = "MinCount";
    private static final String NBT_NBT_TAG = "NbtTag";
    public ResourceLocation nbtTag;

    public NBTRequestTag(ResourceLocation nbtTag, @NotNull TagKey<Item> tag, int count, int minCount) {
        super(tag, count, minCount);
        this.nbtTag = nbtTag;
    }

    public NBTRequestTag(ResourceLocation nbtTag, @NotNull TagKey<Item> tag, @NotNull ItemStack result, int count, int minCount) {
        super(tag, result, count, minCount);
        this.nbtTag = nbtTag;
    }

    public NBTRequestTag(ResourceLocation nbtTag, @NotNull TagKey<Item> tag, int count) {
        super(tag, count);
        this.nbtTag = nbtTag;
    }

    public static CompoundTag serialize(IFactoryController controller, NBTRequestTag input) {
        CompoundTag compound = new CompoundTag();
        compound.putString(NBT_TAG, input.getTag().location().toString());
        if (!ItemStackUtils.isEmpty(input.getResult())) {
            compound.put(NBT_RESULT, input.getResult().serializeNBT());
        }

        compound.putInt(NBT_COUNT, input.getCount());
        compound.putInt(NBT_MINCOUNT, input.getMinimumCount());
        compound.putString(NBT_NBT_TAG, input.nbtTag.toString());
        return compound;
    }

    public static void serialize(IFactoryController controller, FriendlyByteBuf buffer, NBTRequestTag input) {
        RequestTag.serialize(null, buffer, input);
        buffer.writeResourceLocation(input.nbtTag);
    }

    public static NBTRequestTag deserialize(IFactoryController controller, FriendlyByteBuf buffer) {
        TagKey<Item> theTag = ItemTags.create(buffer.readResourceLocation());
        ItemStack result = buffer.readBoolean() ? buffer.readItem() : ItemStack.EMPTY;
        int count = buffer.readInt();
        int minCount = buffer.readInt();
        ResourceLocation nbtTag = buffer.readResourceLocation();
        return new NBTRequestTag(nbtTag, theTag, result, count, minCount);
    }

    public static NBTRequestTag deserialize(IFactoryController controller, CompoundTag compound) {
        TagKey<Item> theTag = ItemTags.create(new ResourceLocation(compound.getString(NBT_TAG)));
        ItemStack result = compound.contains(NBT_RESULT) ? ItemStackUtils.deserializeFromNBT(compound.getCompound(NBT_RESULT)) : ItemStack.EMPTY;
        int count = compound.getInt(NBT_COUNT);
        ResourceLocation nbtTag = new ResourceLocation(compound.getString(NBT_NBT_TAG));
        int minCount = compound.contains(NBT_MINCOUNT) ? compound.getInt(NBT_MINCOUNT) : count;
        return new NBTRequestTag(nbtTag, theTag, result, count, minCount);
    }

    public boolean matches(@NotNull ItemStack item) {
        String tag = item.getTag() != null ? item.getTag().getString("AmmoId") : null;
        boolean matches = tag != null && tag.equals(nbtTag.toString());
        return matches;
    }
}
