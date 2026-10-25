package com.example.starmagic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 별빛 낙하(Starfall):
 * 1) 지팡이에서 나선형 빛줄기가 목표로 날아가고
 * 2) 목표 지점에 회전하는 마법진 + 하늘로 솟는 빛기둥이 생기며
 * 3) 하늘에서 유성이 연달아 떨어지고
 * 4) 마지막에 번개와 대폭발로 마무리된다.
 */
public final class StarfallSpell {
    private static final double RANGE = 40;
    /** true: 진짜 폭발(블록이 부서짐), false: 폭발 연출/피해만 (지형 보호) */
    private static final boolean BREAK_BLOCKS = true;

    private StarfallSpell() {}

    public static void cast(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(
                eye, eye.add(look.scale(RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 target = groundAt(level, hit.getLocation().x, hit.getLocation().z);

        // 1) 시전
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.8f);
        drawBeam(level, eye, target.add(0, 1, 0));

        // 2) 회전하는 마법진 (60틱 동안 계속 다시 그리며 돌린다)
        for (int k = 0; k < 16; k++) {
            final double rotation = k * 0.25;
            DelayedTasks.add(k * 4, () -> drawMagicCircle(level, target, rotation));
        }
        DelayedTasks.add(8, () -> {
            drawPillar(level, target);
            level.playSound(null, target.x, target.y, target.z,
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0f, 1.6f);
        });

        // 3) 유성우
        for (int i = 0; i < 18; i++) {
            DelayedTasks.add(20 + i * 3, () -> {
                double angle = level.random.nextDouble() * Math.PI * 2;
                double radius = level.random.nextDouble() * 6;
                Vec3 impact = groundAt(level, target.x + Math.cos(angle) * radius, target.z + Math.sin(angle) * radius);
                meteor(level, player, impact);
            });
        }

        // 4) 피날레
        DelayedTasks.add(85, () -> finale(level, player, target));
    }

    // ---------- 연출 조각들 ----------

    /** 지팡이 → 목표로 이어지는 코어 빛줄기 + 이중 나선 */
    private static void drawBeam(ServerLevel level, Vec3 eye, Vec3 target) {
        Vec3 dir = target.subtract(eye).normalize();
        Vec3 ref = Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(ref).normalize();
        Vec3 up = right.cross(dir).normalize();
        Vec3 start = eye.add(dir.scale(1.2)).add(0, -0.3, 0);
        double length = start.distanceTo(target);

        for (double t = 0; t < length; t += 0.35) {
            Vec3 c = start.add(dir.scale(t));
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            for (int strand = 0; strand < 2; strand++) {
                double angle = t * 2.0 + strand * Math.PI;
                Vec3 p = c.add(right.scale(Math.cos(angle) * 0.45)).add(up.scale(Math.sin(angle) * 0.45));
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void drawMagicCircle(ServerLevel level, Vec3 c, double rotation) {
        drawRing(level, c, 6.0, 72, rotation, ParticleTypes.END_ROD);
        drawRing(level, c, 4.5, 48, -rotation, ParticleTypes.SOUL_FIRE_FLAME);
        drawStar(level, c, 6.0, rotation, ParticleTypes.END_ROD);
    }

    private static void drawRing(ServerLevel level, Vec3 c, double radius, int points, double rotation, ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double a = rotation + i * 2 * Math.PI / points;
            level.sendParticles(particle, c.x + Math.cos(a) * radius, c.y + 0.15, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** 오각별(펜타그램) */
    private static void drawStar(ServerLevel level, Vec3 c, double radius, double rotation, ParticleOptions particle) {
        Vec3[] v = new Vec3[5];
        for (int i = 0; i < 5; i++) {
            double a = rotation + i * 2 * Math.PI / 5;
            v[i] = new Vec3(c.x + Math.cos(a) * radius, c.y + 0.2, c.z + Math.sin(a) * radius);
        }
        for (int i = 0; i < 5; i++) {
            Vec3 from = v[i];
            Vec3 to = v[(i + 2) % 5];
            for (int s = 0; s <= 12; s++) {
                Vec3 q = from.lerp(to, s / 12.0);
                level.sendParticles(particle, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void drawPillar(ServerLevel level, Vec3 c) {
        for (double y = 0; y < 32; y += 0.6) {
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + y, c.z, 2, 0.25, 0, 0.25, 0.01);
        }
    }

    private static void meteor(ServerLevel level, Player player, Vec3 impact) {
        // 비스듬히 떨어지는 꼬리
        for (double h = 0; h < 24; h += 0.7) {
            level.sendParticles(ParticleTypes.END_ROD,
                    impact.x + h * 0.25, impact.y + h, impact.z, 1, 0.05, 0.05, 0.05, 0);
        }
        // 충돌
        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 0.5, impact.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FIREWORK, impact.x, impact.y + 0.5, impact.z, 25, 0.2, 0.2, 0.2, 0.25);
        level.playSound(null, impact.x, impact.y, impact.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.2f + level.random.nextFloat() * 0.4f);
        boom(level, player, impact, 2.5f);
        damageArea(level, player, impact, 3.0, 6f);
    }

    private static void finale(ServerLevel level, Player player, Vec3 c) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(c);
            bolt.setVisualOnly(true); // 불/데미지 없이 연출만
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 1, c.z, 300, 0.3, 0.3, 0.3, 0.6);

        // 바깥으로 퍼지는 충격파 고리 (count=0이면 dx,dy,dz가 속도 방향이 된다)
        for (int i = 0; i < 60; i++) {
            double a = i * 2 * Math.PI / 60;
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.3, c.z, 0, Math.cos(a), 0.05, Math.sin(a), 0.9);
        }

        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0f, 1.0f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.8f);
        boom(level, player, c, 7.0f);
        damageArea(level, player, c, 8.0, 20f);
    }

    // ---------- 유틸 ----------

    /** 진짜 폭발. 범위 안 플레이어는 잠깐 피해 면역(시전자 보호). */
    private static void boom(ServerLevel level, Player caster, Vec3 c, float power) {
        protectPlayers(level, c, power * 3.0);
        level.explode(caster, c.x, c.y + 0.5, c.z, power,
                BREAK_BLOCKS ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
    }

    private static void protectPlayers(ServerLevel level, Vec3 c, double radius) {
        AABB box = new AABB(c, c).inflate(radius);
        for (Player p : level.getEntitiesOfClass(Player.class, box)) {
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
        }
    }


    private static void damageArea(ServerLevel level, Player caster, Vec3 center, double radius, float damage) {
        AABB box = new AABB(center, center).inflate(radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, en -> en != caster && en.isAlive())) {
            e.hurt(level.damageSources().playerAttack(caster), damage);
            Vec3 push = e.position().subtract(center).normalize().scale(0.6);
            e.push(push.x, 0.5, push.z);
        }
    }

    private static Vec3 groundAt(ServerLevel level, double x, double z) {
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, 0, z));
        return new Vec3(x, pos.getY(), z);
    }
}
