package com.reap.client;

import net.minecraft.client.model.*;

/** Gerado a partir de reap.bbmodel (Blockbench, Bedrock box UV, textura 64x64). */
public final class SpoonModel {
    private static ModelPart cached;

    public static ModelPart get() {
        if (cached == null) cached = build().createModel();
        return cached;
    }

    private static TexturedModelData build() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("c0", ModelPartBuilder.create().uv(0, 17).cuboid(-2f, -1f, -3f, 4f, 1f, 6f, Dilation.NONE),
                ModelTransform.of(0f, 24f, -22f, 0.0f, 0.0f, 0.0f));
        root.addChild("c1", ModelPartBuilder.create().uv(0, 32).cuboid(0f, -1f, -1f, 2f, 1f, 2f, Dilation.NONE),
                ModelTransform.of(3f, 24f, -22f, 0.0f, 0.0f, -0.3927f));
        root.addChild("c2", ModelPartBuilder.create().uv(8, 32).cuboid(-2f, -1f, -1f, 2f, 1f, 2f, Dilation.NONE),
                ModelTransform.of(-3f, 24f, -22f, 0.0f, 0.0f, 0.3927f));
        root.addChild("c3", ModelPartBuilder.create().uv(0, 24).cuboid(-1.9038f, -0.97786f, -3.49302f, 2.0f, 1.0f, 3.5f, new Dilation(-0.001f)),
                ModelTransform.of(4.75f, 23.25f, -23f, -0.40125f, 0.20132f, -0.47734f));
        root.addChild("c4", ModelPartBuilder.create().uv(24, 0).cuboid(-0.0962f, -0.97786f, -3.49302f, 2.0f, 1.0f, 3.5f, new Dilation(-0.001f)),
                ModelTransform.of(-4.75f, 23.25f, -23f, -0.40125f, -0.20132f, 0.47734f));
        root.addChild("c5", ModelPartBuilder.create().uv(24, 4).cuboid(-1.9038f, -0.97786f, -0.00698f, 2.0f, 1.0f, 3.5f, new Dilation(-0.001f)),
                ModelTransform.of(4.75f, 23.25f, -21f, 0.40125f, -0.20132f, -0.47734f));
        root.addChild("c6", ModelPartBuilder.create().uv(10, 24).cuboid(-0.0962f, -0.97786f, -0.00698f, 2.0f, 1.0f, 3.5f, new Dilation(-0.001f)),
                ModelTransform.of(-4.75f, 23.25f, -21f, 0.40125f, 0.20132f, 0.47734f));
        root.addChild("c7", ModelPartBuilder.create().uv(20, 15).cuboid(-3f, -1f, -2.5f, 1f, 1f, 5.0f, Dilation.NONE),
                ModelTransform.of(0f, 24f, -22f, 0.0f, 0.0f, 0.0f));
        root.addChild("c8", ModelPartBuilder.create().uv(20, 21).cuboid(2f, -1f, -2.5f, 1f, 1f, 5.0f, Dilation.NONE),
                ModelTransform.of(0f, 24f, -22f, 0.0f, 0.0f, 0.0f));
        root.addChild("c9", ModelPartBuilder.create().uv(20, 27).cuboid(-1f, -1f, -3f, 2f, 1f, 3f, Dilation.NONE),
                ModelTransform.of(0f, 24f, -25f, -0.7854f, 0.0f, 0.0f));
        root.addChild("c10", ModelPartBuilder.create().uv(0, 28).cuboid(-2f, -1f, -3.25f, 2f, 1f, 3.25f, new Dilation(-0.001f)),
                ModelTransform.of(3f, 24f, -25f, -0.79305f, 0.1231f, -0.12404f));
        root.addChild("c11", ModelPartBuilder.create().uv(10, 28).cuboid(0f, -1f, -3.25f, 2f, 1f, 3.25f, new Dilation(-0.001f)),
                ModelTransform.of(-3f, 24f, -25f, -0.79305f, -0.1231f, 0.12404f));
        root.addChild("c12", ModelPartBuilder.create().uv(30, 27).cuboid(0f, -1f, 0f, 2f, 1f, 3.25f, new Dilation(-0.001f)),
                ModelTransform.of(-3f, 24f, -19f, 0.79305f, 0.1231f, 0.12404f));
        root.addChild("c13", ModelPartBuilder.create().uv(20, 31).cuboid(-2f, -1f, 0f, 2f, 1f, 3.25f, new Dilation(-0.001f)),
                ModelTransform.of(3f, 24f, -19f, 0.79305f, -0.1231f, -0.12404f));
        root.addChild("c14", ModelPartBuilder.create().uv(30, 31).cuboid(-1f, -1f, 0f, 2f, 1f, 3f, Dilation.NONE),
                ModelTransform.of(0f, 24f, -19f, 0.7854f, 0.0f, 0.0f));
        root.addChild("c15", ModelPartBuilder.create().uv(0, 9).cuboid(-1.75f, -1.0f, 0.0f, 3.5f, 1.0f, 7.0f, Dilation.NONE),
                ModelTransform.of(0f, 22.17157f, -12.58579f, 0.17453f, 0.0f, 0.0f));
        root.addChild("c16", ModelPartBuilder.create().uv(20, 9).cuboid(-1.5f, -1.0f, 0.0f, 3.0f, 1.0f, 5.0f, Dilation.NONE),
                ModelTransform.of(0f, 22.17157f, -17.58579f, 0.0f, 0.0f, 0.0f));
        root.addChild("c17", ModelPartBuilder.create().uv(0, 0).cuboid(-2.25f, -1.0f, 0.0f, 4.5f, 1.0f, 8.0f, Dilation.NONE),
                ModelTransform.of(0f, 20.94752f, -6.08222f, -0.04363f, 0.0f, 0.0f));
        root.addChild("c18", ModelPartBuilder.create().uv(32, 15).cuboid(-1.75f, -1.0f, 8.0f, 3.5f, 1.0f, 1.0f, Dilation.NONE),
                ModelTransform.of(0f, 20.94752f, -6.08222f, -0.04363f, 0.0f, 0.0f));
        return TexturedModelData.of(data, 64, 64);
    }
}
