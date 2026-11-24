package dev.xetyk.miniassist.util;

import dev.xetyk.miniassist.modules.api.MultiBooleanSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;

public final class TargetSelector {
    private TargetSelector() {}
    public static boolean isValidType(LivingEntity entity, MultiBooleanSetting targets) {
        if (entity instanceof PlayerEntity) return targets.isSelected("Игроков");
        if (entity instanceof Monster) return targets.isSelected("Враждебных");
        return targets.isSelected("Мирных");
    }
}
