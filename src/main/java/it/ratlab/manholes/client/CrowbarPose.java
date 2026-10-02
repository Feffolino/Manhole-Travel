// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.ratlab.manholes.item.PryTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IArmPoseTransformer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * The crowbar while the local player pries: no mining / swing animation, the bar tilted down into the cover, and a
 * short downward "pump" on every accepted mash press (from {@link PryHud#pump}). First person through
 * {@link #applyForgeHandTransform}, third person (F5) through a custom {@link HumanoidModel.ArmPose}.
 */
public final class CrowbarPose implements IClientItemExtensions {
    /** Parameters of {@code HumanoidModel.ArmPose.MANHOLES_PRY}: one-handed, arm raised forward and pushed down. */
    public static final HumanoidModel.ArmPose PRY_POSE = HumanoidModel.ArmPose.create("MANHOLES_PRY",
            false, (IArmPoseTransformer) CrowbarPose::thirdPerson);

    private static boolean prying(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        return entity == mc.player && PryHud.active();
    }

    private static void thirdPerson(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float pump = PryHud.pump(Minecraft.getInstance().getFrameTime());
        part.xRot = -1.1f + pump * 0.45f;
        part.yRot = (arm == HumanoidArm.RIGHT ? -0.15f : 0.15f);
        part.zRot = 0f;
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return prying(entity) && PryTools.isPryTool(stack) ? PRY_POSE : null;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand,
            float partialTick, float equipProcess, float swingProcess) {
        if (!prying(player) || !PryTools.isPryTool(itemInHand)) {
            return false;
        }
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float pump = PryHud.pump(partialTick);
        // Vanilla's item arm transform, lowered a bit, then the bar tilted down into the cover; a press pushes it down.
        poseStack.translate(side * 0.56f, -0.62f + equipProcess * -0.6f - pump * 0.10f, -0.72f + pump * 0.04f);
        poseStack.mulPose(Axis.XP.rotationDegrees(-32f - pump * 22f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 6f));
        return true;
    }
}
