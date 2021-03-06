package engine.graphics.internal.graph;

import engine.graphics.Scene3D;
import engine.graphics.graph.DrawDispatcher;
import engine.graphics.graph.Drawer;
import engine.graphics.graph.FrameContext;
import engine.graphics.graph.Renderer;
import engine.graphics.queue.RenderType;
import engine.graphics.shader.ShaderResource;
import engine.graphics.shader.UniformBlock;
import engine.graphics.util.Struct;
import engine.graphics.viewport.Viewport;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;

public class ShadowOpaqueDrawDispatcher implements DrawDispatcher {
    private final Viewport viewport;

    private final Matrix4f tempMatrix4f = new Matrix4f();

    private UniformBlock uniformMatrices;

    private static class Matrices implements Struct {
        private Matrix4fc proj;
        private Matrix4fc modelView;

        public Matrices(Matrix4fc proj, Matrix4fc modelView) {
            this.proj = proj;
            this.modelView = modelView;
        }

        @Override
        public int sizeof() {
            return 128;
        }

        @Override
        public ByteBuffer get(int index, ByteBuffer buffer) {
            proj.get(index, buffer);
            modelView.get(index + 64, buffer);
            return buffer;
        }
    }

    public ShadowOpaqueDrawDispatcher(Viewport viewport) {
        this.viewport = viewport;
    }

    @Override
    public void init(Drawer drawer) {
        ShaderResource resource = drawer.getShaderResource();
        this.uniformMatrices = resource.getUniformBlock("Matrices");
    }

    @Override
    public void draw(FrameContext frameContext, Drawer drawer, Renderer renderer) {
        ShaderResource resource = drawer.getShaderResource();
        Scene3D scene = viewport.getScene();
        scene.getRenderQueue().getGeometryList(RenderType.OPAQUE).forEach(geometry -> {
            Matrix4f transformMatrix = geometry.getWorldTransform().getTransformMatrix(tempMatrix4f);
            uniformMatrices.set(new Matrices( // TODO: optimize it
                    viewport.getProjectionMatrix(),
                    viewport.getViewMatrix().mul(transformMatrix, transformMatrix)));
            resource.refresh();
            renderer.drawMesh(geometry.getMesh());
        });
    }
}
