package dev.topclient.client.clover.mixin;

import dev.topclient.client.clover.module.TotemLook;
import dev.topclient.client.clover.module.TotemParticles;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TotemParticle.class, priority = 2000)
public abstract class TotemParticleMixin extends SimpleAnimatedParticle implements TotemLook {
	@Unique
	private static boolean topclient$spawningExtra;

	@Unique
	private float topclient$seed = -1F;

	protected TotemParticleMixin(ClientLevel world, double x, double y, double z, SpriteSet spriteProvider, float upwardsAcceleration) {
		super(world, x, y, z, spriteProvider, upwardsAcceleration);
	}

	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void topclient$created(CallbackInfo ci) {
		try {
			TotemParticles t = TotemParticles.active();
			if (t == null) {
				return;
			}
			topclient$seed = this.random.nextFloat();
			float sp = t.speed.getFloat();
			this.xd *= sp;
			this.yd *= sp;
			this.zd *= sp;
			this.lifetime = Math.max(2, Math.round(this.lifetime * t.lifetime.getFloat()));
			this.scale(t.size.getFloat());
			topclient$apply();

			if (!topclient$spawningExtra) {
				float amount = t.multiplier.getFloat();
				if (amount < 1F) {
					if (this.random.nextFloat() > amount) {
						this.remove();
					}
				} else {
					int extra = (int) (amount - 1F);
					if (this.random.nextFloat() < amount - 1F - extra) {
						extra++;
					}
					topclient$spawningExtra = true;
					try {
						for (int i = 0; i < extra; i++) {
							double j = 0.85 + this.random.nextFloat() * 0.3;
							double k = 0.85 + this.random.nextFloat() * 0.3;
							double l = 0.85 + this.random.nextFloat() * 0.3;
							this.level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, this.x, this.y, this.z,
									this.xd / sp * j, this.yd / sp * k, this.zd / sp * l);
						}
					} finally {
						topclient$spawningExtra = false;
					}
				}
			}
		} catch (RuntimeException e) {
		}
	}

	@Override
	public void topclient$apply() {
		TotemParticles t = TotemParticles.active();
		if (t == null || topclient$seed < 0F) {
			return;
		}
		float life = this.lifetime <= 0 ? 0F : Math.min(1F, this.age / (float) this.lifetime);
		if (t.ownTextures.getBool()) {
			TextureAtlasSprite s = TotemParticles.sprite(life);
			if (s != null) {
				this.setSprite(s);
			}
		}
		if (t.colourMode.getMode() != 3) {
			int c = t.colourFor(topclient$seed, life);
			this.setColor(((c >> 16) & 0xFF) / 255F, ((c >> 8) & 0xFF) / 255F, (c & 0xFF) / 255F);
		}
	}

	@Override
	public boolean topclient$glows() {
		TotemParticles t = TotemParticles.active();
		return t != null && topclient$seed >= 0F && t.glow.getBool();
	}
}
