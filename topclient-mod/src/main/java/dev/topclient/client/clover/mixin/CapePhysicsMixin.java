package dev.topclient.client.clover.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.topclient.client.clover.module.CapePhysics;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Cape Physics: the game still decides whether a cape shows (invisible, elytra, settings...);
 * only the final "draw the stiff cape" step is swapped for the waving strips from CapePhysics.
 * Optional hook (require = 0): if Minecraft changes, capes are simply drawn the normal way.
 */
@Mixin(CapeLayer.class)
public class CapePhysicsMixin {
	@Redirect(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"),
			require = 0)
	private void topclient$wavyCape(SubmitNodeCollector queue, Model model, Object st, PoseStack matrices, RenderType layer, int light, int overlay, int outline,
			ModelFeatureRenderer.CrumblingOverlay crumbling) {
		if (CapePhysics.active() == null || !(st instanceof AvatarRenderState s) || !(model instanceof PlayerCapeModel cape)
				|| s.skin == null || s.skin.cape() == null) {
			queue.submitModel(model, st, matrices, layer, light, overlay, outline, crumbling);
			return;
		}
		try {
			Identifier tex = s.skin.cape().texturePath();
			float[][] quads = CapePhysics.step(s.id, s.capeFlap, s.capeLean, s.capeLean2);
			cape.setupAnim(s); // the body pose (sneaking etc.), exactly as the game would set it
			matrices.pushPose();
			cape.body.translateAndRotate(matrices);
			matrices.translate(0F, 0F, 2F / 16F); // where the cape hangs from the shoulders
			queue.submitCustomGeometry(matrices, RenderTypes.entityCutout(tex), (entry, vc) -> {
				for (float[] q : quads) {
					for (int k = 0; k < 4; k++) {
						vc.addVertex(entry, q[k * 5] / 16F, q[k * 5 + 1] / 16F, q[k * 5 + 2] / 16F)
								.setColor(-1)
								.setUv(q[k * 5 + 3] / 64F, q[k * 5 + 4] / 32F)
								.setOverlay(OverlayTexture.NO_OVERLAY)
								.setLight(light)
								.setNormal(entry, q[20], q[21], q[22]);
					}
				}
			});
			matrices.popPose();
		} catch (RuntimeException e) {
			queue.submitModel(model, st, matrices, layer, light, overlay, outline, crumbling);
		}
	}
}
