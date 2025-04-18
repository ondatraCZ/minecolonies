package com.minecolonies.core.entity.ai.workers.guard;


import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.ai.combat.AttackMoveAI;
import com.minecolonies.core.entity.ai.combat.CombatUtils;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.PathfindingUtils;
import com.minecolonies.core.entity.pathfinding.PathingOptions;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobCanSee;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveAwayFromLocation;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveToLocation;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Optional;

import static com.minecolonies.api.util.constant.GuardConstants.*;
import static com.minecolonies.api.util.constant.StatisticsConstants.MOBS_KILLED;
import static com.minecolonies.api.util.constant.StatisticsConstants.MOB_KILLED;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.STATS_MODULE;
import static com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIFight.SPEED_LEVEL_BONUS;
import static com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard.PATROL_DEVIATION_RAID_POINT;

public class GunnerCombatAI extends AttackMoveAI<EntityCitizen> {
    /**
     * Visible combat icon
     */
    private final static VisibleCitizenStatus ARCHER_COMBAT =
            new VisibleCitizenStatus(new ResourceLocation(Constants.MOD_ID, "textures/icons/work/archer_combat.png"), "com.minecolonies.gui.visiblestatus.archer_combat");
    private final EntityCitizen owner;
    private final EntityAIGunner parentAI;
    private boolean midAim = false;

    /**
     * The value of the speed which the guard will move.
     */
    private static final double COMBAT_SPEED = 1.0;

    /**
     * Bonus range for shooting while guarding
     */
    private static final int GUARD_BONUS_RANGE = 10;
    /**
     * Flee chance
     */
    private static final int FLEE_CHANCE = 3;

    private final PathingOptions combatPathingOptions;

    public GunnerCombatAI(
            final EntityCitizen owner,
            final ITickRateStateMachine stateMachine,
            final EntityAIGunner parentAI)
    {
        super(owner, stateMachine);
        this.owner = owner;
        this.parentAI = parentAI;
        combatPathingOptions = new PathingOptions();
        combatPathingOptions.setEnterDoors(true);
        combatPathingOptions.setCanOpenDoors(true);
        combatPathingOptions.setCanSwim(true);
        combatPathingOptions.withOnPathCost(0.8);
        combatPathingOptions.withJumpCost(0.01);
        combatPathingOptions.withDropCost(1.5);
    }

    @Override
    public boolean canAttack()
    {
        final int weaponSlot =
                InventoryUtils.getFirstSlotOfItemHandlerContainingEquipment(user.getInventoryCitizen(), ModEquipmentTypes.gun.get(), 0, user.getCitizenData().getWorkBuilding().getMaxEquipmentLevel());

        if (weaponSlot != -1)
        {
            CitizenItemUtils.setHeldItem(user, InteractionHand.MAIN_HAND, weaponSlot);
            if (nextAttackTime  >= user.level.getGameTime() && !user.isUsingItem())
            {
                user.startUsingItem(InteractionHand.MAIN_HAND);
            }
            return true;
        }

        return false;
    }

    @Override
    protected boolean checkForTarget()
    {
        final boolean validTarget = super.checkForTarget();

        if (!validTarget && user.isUsingItem())
        {
            user.stopUsingItem();
        }

        return validTarget;
    }
    private ShootResult shoot(){
        return  ((IGunOperator) user).shoot(
                () -> calculatePitch(owner, target),
                () -> calculateYaw(owner, target)
        );
    }

    @Override
    protected void doAttack(final LivingEntity target)
    {
        if (user.distanceToSqr(target) < RANGED_FLEE_SQDIST)
        {
            if (user.getRandom().nextInt(FLEE_CHANCE) == 0 &&
                    !((AbstractBuildingGuards) user.getCitizenData().getWorkBuilding()).getTask().equals(GuardTaskSetting.GUARD))
            {
                EntityNavigationUtils.walkAwayFrom(user, target.blockPosition(), (int) (getAttackDistance() / 2.0), getCombatMovementSpeed());
            }
        }
        else
        {
            user.getNavigation().stop();
        }
        user.getCitizenData().setVisibleStatus(ARCHER_COMBAT);
        IGunOperator gunOperator = (IGunOperator) user;
        AbstractGunItem gun = (AbstractGunItem) parentAI.getGunStack().getItem();
        if(gun.getCurrentAmmoCount(parentAI.getGunStack()) <= 0)
        {
            gunOperator.reload();
        }
        user.lookAt(target, 1f,1f);
        Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(gun.getGunId(parentAI.getGunStack()));
        float inaccRatio = gunIndex.map(commonGunIndex -> (commonGunIndex.getGunData().getInaccuracy(InaccuracyType.STAND)/commonGunIndex.getGunData().getInaccuracy(InaccuracyType.AIM))).orElse(1f);
        if(inaccRatio > 100){
            gunOperator.aim(true);
            if(gunOperator.getSynAimingProgress() < 1f && !midAim) {
                midAim = true;
                return;
            }
        }
        midAim = false;
        ShootResult shootResult = shoot();
        if(shootResult == ShootResult.NEED_BOLT){
            gunOperator.bolt();
            shoot();
        }
        else if(shootResult == ShootResult.NOT_DRAW) {
            gunOperator.draw(parentAI::getGunStack);
            shoot();
        }
        gunOperator.aim(false);
        user.stopUsingItem();
        user.decreaseSaturationForContinuousAction();
    }
    private float calculateYaw(Entity owner, Entity target) {
        double dx = target.getX() - owner.getX();
        double dz = target.getZ() - owner.getZ();
        // Use Math.atan2 to calculate yaw and convert to degrees
        return (float) (Math.atan2(dz, dx) * (180 / Math.PI)) - 90.0F; // -90 to align with Minecraft's yaw system
    }

    private float calculatePitch(Entity owner, Entity target) {
        double dx = target.getX() - owner.getX();
        double dy = target.getY() + target.getEyeHeight() - (owner.getY() + owner.getEyeHeight());
        double dz = target.getZ() - owner.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz); // Horizontal distance
        // Use Math.atan2 to calculate pitch and convert to degrees
        return (float) -(Math.atan2(dy, distance) * (180 / Math.PI));
    }

    @Override
    protected double getAttackDistance()
    {
        int attackDist = BASE_DISTANCE_FOR_RANGED_ATTACK;
        // + 1 Blockrange per building level for a total of +5 from building level
        if (user.getCitizenData().getWorkBuilding() != null)
        {
            attackDist += user.getCitizenData().getWorkBuilding().getBuildingLevel()*5;
        }
        // ~ +1 each three levels for a total of +10 from guard level
        if (user.getCitizenData() != null)
        {
            attackDist += (user.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability) / 50.0f) * 45;
        }

        if (target != null)
        {
            attackDist += user.getY() - target.getY();
        }

        if (((AbstractBuildingGuards) user.getCitizenData().getWorkBuilding()).getTask().equals(GuardTaskSetting.GUARD))
        {
            attackDist += 30;
        }

        return attackDist;
    }

    @Override
    protected int getAttackDelay()
    {
        if(midAim) return 10;
        AbstractGunItem gun = (AbstractGunItem) parentAI.getGunStack().getItem();
        Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(gun.getGunId(parentAI.getGunStack()));
        return gunIndex.map(commonGunIndex -> 1 / (commonGunIndex.getGunData().getRoundsPerMinute() / 60) * 20).orElse(40);
    }

    @Override
    protected PathResult moveInAttackPosition(final LivingEntity target)
    {
        if (BlockPosUtil.getDistanceSquared(target.blockPosition(), user.blockPosition()) <= 4.0)
        {
            final PathJobMoveAwayFromLocation job = new PathJobMoveAwayFromLocation(user.level,
                    PathfindingUtils.prepareStart(target),
                    target.blockPosition(),
                    (int) 7.0,
                    (int) user.getAttribute(Attributes.FOLLOW_RANGE).getValue(),
                    user);
            final PathResult pathResult = ((MinecoloniesAdvancedPathNavigate) user.getNavigation()).setPathJob(job, null, getCombatMovementSpeed(), true);
            job.setPathingOptions(combatPathingOptions);
            return pathResult;
        }
        else if (BlockPosUtil.getDistance2D(target.blockPosition(), user.blockPosition()) >= 20)
        {
            final PathJobMoveToLocation job = new PathJobMoveToLocation(user.level, PathfindingUtils.prepareStart(user), target.blockPosition(), 200, user);
            final PathResult pathResult = ((MinecoloniesAdvancedPathNavigate) user.getNavigation()).setPathJob(job, null, getCombatMovementSpeed(), true);
            job.setPathingOptions(combatPathingOptions);
            return pathResult;
        }
        final PathJobCanSee job = new PathJobCanSee(user, target, user.level, ((AbstractBuildingGuards) user.getCitizenData().getWorkBuilding()).getGuardPos(), 40);
        final PathResult pathResult = ((MinecoloniesAdvancedPathNavigate) user.getNavigation()).setPathJob(job, null, getCombatMovementSpeed(), true);
        job.setPathingOptions(combatPathingOptions);
        return pathResult;
    }

    /**
     * Get combat speed
     *
     * @return movent speed
     */
    protected double getCombatMovementSpeed()
    {
        double levelAdjustment = user.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Agility) * SPEED_LEVEL_BONUS;
        levelAdjustment += (user.getCitizenData().getWorkBuilding().getBuildingLevel() * 2 - 1) * SPEED_LEVEL_BONUS;

        levelAdjustment = Math.min(levelAdjustment, 0.3);
        return COMBAT_SPEED + levelAdjustment;
    }

    @Override
    protected boolean isAttackableTarget(final LivingEntity entity)
    {
        return AbstractEntityAIGuard.isAttackableTarget(user, entity);
    }

    @Override
    protected boolean isWithinPersecutionDistance(final LivingEntity target)
    {
        return parentAI.isWithinPersecutionDistance(target.blockPosition(), getAttackDistance());
    }

    @Override
    protected boolean skipSearch(final LivingEntity entity)
    {
        // Found a sleeping guard nearby
        if (entity instanceof EntityCitizen)
        {
            final EntityCitizen citizen = (EntityCitizen) entity;
            if (citizen.getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard && ((AbstractJobGuard<?>) citizen.getCitizenJobHandler().getColonyJob()).isAsleep()
                    && user.getSensing().hasLineOfSight(citizen))
            {
                parentAI.setWakeCitizen(citizen);
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onTargetChange()
    {
        CombatUtils.notifyGuardsOfTarget(user, target, PATROL_DEVIATION_RAID_POINT);
    }

    @Override
    protected int getYSearchRange()
    {
        if (((AbstractBuildingGuards) user.getCitizenData().getWorkBuilding()).getTask().equals(GuardTaskSetting.GUARD))
        {
            return Y_VISION + 25;
        }

        return Y_VISION;
    }

    @Override
    protected void onTargetDied(final LivingEntity entity)
    {
        parentAI.incrementActionsDoneAndDecSaturation();
        user.getCitizenExperienceHandler().addExperience(EXP_PER_MOB_DEATH);
        user.getCitizenColonyHandler().getColonyOrRegister().getStatisticsManager().increment(MOBS_KILLED, user.getCitizenColonyHandler().getColonyOrRegister().getDay());
        if (entity.getType().getDescription().getContents() instanceof TranslatableContents translatableContents)
        {
            parentAI.building.getModule(STATS_MODULE).increment(MOB_KILLED + ";" + translatableContents.getKey());
        }
    }
}


