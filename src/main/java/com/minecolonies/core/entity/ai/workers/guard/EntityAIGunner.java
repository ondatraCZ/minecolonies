package com.minecolonies.core.entity.ai.workers.guard;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.jobs.JobGunner;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobWalkRandomEdge;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

public class EntityAIGunner extends AbstractEntityAIGuard<JobGunner, AbstractBuildingGuards>{

    public EntityAIGunner(@NotNull final JobGunner job)
    {
        super(job);
        toolsNeeded.add(ModEquipmentTypes.gun.get());//TODO
        new GunnerCombatAI((EntityCitizen) worker, getStateAI(), this);
    }

    public ItemStack getGunStack()
    {
       if(worker.getHandSlots().iterator().hasNext()) {
           ItemStack gunStack = worker.getHandSlots().iterator().next();
           return gunStack.getItem() instanceof AbstractGunItem ? gunStack : ItemStack.EMPTY;
       }
       return ItemStack.EMPTY;
    }


    @Override
    protected void atBuildingActions() {
        super.atBuildingActions();
        IGunOperator operator = (IGunOperator) worker;
        if(getGunStack() != ItemStack.EMPTY) {
            operator.draw(this::getGunStack);
        }
        InventoryUtils.transferXOfFirstSlotInProviderWithIntoNextFreeSlotInItemHandler(building,
                item -> item.getItem() instanceof IAmmo,
                64,
                worker.getInventoryCitizen());
        hasTool();
        if (InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), this::itemIsCorrectAmmo) < 64 && super.hasTool())
        {
            Optional<CommonGunIndex> optional = TimelessAPI.getCommonGunIndex(((AbstractGunItem)getGunStack().getItem()).getGunId(getGunStack()));
            if(optional.isPresent()) {
                CommonGunIndex gunIndex = optional.get();
                ResourceLocation ammoId = gunIndex.getGunData().getAmmoId();
                CheckOrCreateAmmoRequest(TagKey.create(BuiltInRegistries.ITEM.key(), ammoId).location(), gunIndex.getGunData().getAmmoAmount()*5, 1);
            }

            }
    }

    public boolean itemIsCorrectAmmo(ItemStack item)
    {
        if(!(item.getItem() instanceof IAmmo ammo)) return false;
        AbstractGunItem gun = (AbstractGunItem) getGunStack().getItem();
        Optional<CommonGunIndex> optional = TimelessAPI.getCommonGunIndex(gun.getGunId(getGunStack()));
        if(optional.isPresent())
        {
            CommonGunIndex gunIndex = optional.get();
            ResourceLocation ammoId = gunIndex.getGunData().getAmmoId();
            return ammoId.equals(ammo.getAmmoId(item));
        }
        return false;
    }
    @Override
    public void guardMovement()
    {
        if (worker.getRandom().nextInt(3) < 1)
        {
            walkToSafePos(buildingGuards.getGuardPos());
            return;
        }

        if ((BlockPosUtil.dist(buildingGuards.getGuardPos(), worker.blockPosition()) <= 10 || walkToSafePos(buildingGuards.getGuardPos()))
                || Math.abs(buildingGuards.getGuardPos().getY() - worker.blockPosition().getY()) > 3)
        {
            // Moves the ranger randomly to close edges, for better vision to mobs
            ((MinecoloniesAdvancedPathNavigate) worker.getNavigation()).setPathJob(new PathJobWalkRandomEdge(world, buildingGuards.getGuardPos(), 20, worker),
                    null,
                    1.0, true);
        }
    }
    private IToken lastToken;
    private ResourceLocation lastBulletKey;
    public boolean CheckOrCreateAmmoRequest(ResourceLocation ammoId, int count, int minCount) {
        if (ammoId != this.lastBulletKey && this.lastBulletKey != null && this.lastToken != null) {
            this.worker.getCitizenColonyHandler().getColonyOrRegister().getRequestManager().updateRequestState(this.lastToken, RequestState.CANCELLED);
            this.lastToken = null;
        }

        this.lastBulletKey = ammoId;
        RequestTag deliverable = new NBTRequestTag(ammoId, TagKey.create(Registries.ITEM, new ResourceLocation("tacz", "ammo")), count, minCount);
        ItemStack ammoStack = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("tacz", "ammo")), count);
        ammoStack.getOrCreateTag().putString(ammoId.getNamespace(), ammoId.getPath());
        deliverable.setResult(ammoStack);
        InventoryCitizen inventoryCitizen = this.worker.getInventoryCitizen();
        int invCount = InventoryUtils.getItemCountInItemHandler(inventoryCitizen, deliverable::matches);
        if (invCount >= count) {
            return true;
        } else {
            int updatedCount = count - invCount;
            int updatedMinCount = Math.min(updatedCount, minCount);
            if (InventoryUtils.hasBuildingEnoughElseCount(building, deliverable::matches, updatedMinCount) >= updatedMinCount) {
                if (InventoryUtils.transferXOfFirstSlotInProviderWithIntoNextFreeSlotInItemHandler(building, deliverable::matches, updatedCount, this.worker.getInventoryCitizen())) {
                    return true;
                }
            }
            if (this.building.getOpenRequestsOfTypeFiltered(this.worker.getCitizenData(), TypeConstants.DELIVERABLE, (r) -> r.getRequest().getClass().equals(deliverable.getClass())).isEmpty() && ((AbstractBuildingGuards)this.building).getCompletedRequestsOfTypeFiltered(this.worker.getCitizenData(), TypeConstants.DELIVERABLE, (r) -> ((IDeliverable)r.getRequest()).getClass().equals(deliverable.getClass())).isEmpty()) {
                this.worker.getCitizenData().createRequest(deliverable);
            }

            return false;
        }
    }
}
