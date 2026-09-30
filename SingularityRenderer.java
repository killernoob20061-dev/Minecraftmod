package com.worldeater.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.worldeater.SingularityEntity;
import com.worldeater.WorldEater;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Low-poly sphere and visibly segmented rotating ring, using vanilla rendering only. */
public final class SingularityRenderer extends EntityRenderer<SingularityEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WorldEater.MOD_ID, "textures/entity/singularity.png");
    private static final float[][] SPHERE = sphere();

    public SingularityRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public void render(SingularityEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        pose.pushPose();
        float r = entity.radius();
        pose.scale(r, r, r);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (float[] v : SPHERE) vertex(vertices, pose.last(), v[0], v[1], v[2], 0xFF080510);
        pose.mulPose(Axis.XP.rotationDegrees(22));
        pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 4));
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            double b = (i + 1) * Math.PI * 2 / 48;
            int color = i % 6 < 2 ? 0xFFDBB2FF : 0xFF7942C4;
            vertex(vertices, pose.last(), (float) Math.cos(a) * 1.2F, 0, (float) Math.sin(a) * 1.2F, color);
            vertex(vertices, pose.last(), (float) Math.cos(a) * 1.5F, 0, (float) Math.sin(a) * 1.5F, color);
            vertex(vertices, pose.last(), (float) Math.cos(b) * 1.5F, 0, (float) Math.sin(b) * 1.5F, color);
            vertex(vertices, pose.last(), (float) Math.cos(b) * 1.2F, 0, (float) Math.sin(b) * 1.2F, color);
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, int color) {
        out.addVertex(pose, x, y, z).setColor(color).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    private static float[][] sphere() {
        float[][] points = new float[12 * 24 * 4][3];
        int index = 0;
        for (int lat = 0; lat < 12; lat++) {
            for (int lon = 0; lon < 24; lon++) {
                for (int corner = 0; corner < 4; corner++) {
                    double phi = ((lat + (corner >= 2 ? 1 : 0)) / 12.0 - 0.5) * Math.PI;
                    double theta = (lon + (corner == 1 || corner == 2 ? 1 : 0)) / 24.0 * Math.PI * 2;
                    points[index++] = new float[]{(float) (Math.cos(phi) * Math.cos(theta)), (float) Math.sin(phi),
                            (float) (Math.cos(phi) * Math.sin(theta))};
                }
            }
        }
        return points;
    }

    @Override
    public ResourceLocation getTextureLocation(SingularityEntity entity) { return TEXTURE; }
}
