package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;

public abstract class AbstractDragonSittingPhase extends AbstractDragonPhaseInstance {
    public AbstractDragonSittingPhase(EnderDragon dragon) {
        super(dragon);
    }

    @Override
    public boolean isSitting() {
        return true;
    }

    @Override
    public float onHurt(DamageSource damageSource, float damage) {
        if (!(damageSource.getDirectEntity() instanceof AbstractArrow) && !(damageSource.getDirectEntity() instanceof WindCharge)) {
            return super.onHurt(damageSource, damage);
        } else {
            damageSource.getDirectEntity().igniteForSeconds(1.0F);
            return 0.0F;
        }
    }
}
