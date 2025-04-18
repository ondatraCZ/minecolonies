package com.minecolonies.core.colony.jobs;

import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.entity.ai.workers.guard.EntityAIGunner;
import com.minecolonies.core.entity.ai.workers.guard.EntityAIRanger;
import net.minecraft.resources.ResourceLocation;

public class JobGunner extends AbstractJobGuard<JobGunner>{

    /**
     * The name associated with the job.
     */
    public static final String DESC = "com.minecolonies.coremod.job.Ranger";

    /**
     * Initialize citizen data.
     *
     * @param entity the citizen data.
     */
    public JobGunner(final ICitizenData entity)
    {
        super(entity);
    }

    @Override
    public EntityAIGunner generateGuardAI()
    {
        return new EntityAIGunner(this);
    }

    @Override
    public ResourceLocation getModel()
    {
        return ModModelTypes.ARCHER_GUARD_ID;
    }
}
