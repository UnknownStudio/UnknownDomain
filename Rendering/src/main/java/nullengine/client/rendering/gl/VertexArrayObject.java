package nullengine.client.rendering.gl;

import nullengine.client.rendering.gl.vertex.GLVertexElement;
import org.apache.commons.lang3.Validate;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

public class VertexArrayObject {

    private int id;

    private int vertexCount;

    private VertexAttribute[] attributes;

    private VertexBufferObject indices;
    private GLDataType indexType;
    private GLDrawMode drawMode;

    private VertexArrayObject() {
    }

    public VertexAttribute getAttribute(int index) {
        return attributes[index];
    }

    public int getAttributeLength() {
        return attributes.length;
    }

    public VertexBufferObject getIndices() {
        return indices;
    }

    public GLDataType getIndexType() {
        return indexType;
    }

    public GLDrawMode getDrawMode() {
        return drawMode;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public void setVertexCount(int vertexCount) {
        this.vertexCount = vertexCount;
    }

    public void refreshAttribute() {
        bind();
        for (int i = 0; i < attributes.length; i++) {
            attributes[i].apply(i);
        }
    }

    public void bind() {
        if (id == 0) {
            throw new IllegalStateException("Object has been disposed");
        }
        GL30.glBindVertexArray(id);
    }

    public void unbind() {
        GL30.glBindVertexArray(0);
    }

    public void drawArrays() {
        GL11.glDrawArrays(drawMode.gl, 0, vertexCount);
    }

    public void drawElements() {
        GL30.glDrawElements(drawMode.gl, vertexCount, indexType.glId, 0);
    }

    public void draw() {
        bind();
        if (indices == null) {
            drawArrays();
        } else {
            drawElements();
        }
    }

    public void dispose() {
        if (id != 0) {
            for (VertexAttribute attribute : attributes) {
                attribute.dispose();
            }
            GL30.glDeleteVertexArrays(id);
            id = 0;
        }
    }

    public static final class VertexAttribute {
        private GLVertexElement element;
        private Object value;
        private VertexAttributeType type;

        private boolean needUpdate = false;

        public VertexAttribute(GLVertexElement element, Object value) {
            this.element = element;
            setValue(value);
        }

        public GLVertexElement getElement() {
            return element;
        }

        public Object getValue() {
            return value;
        }

        public VertexBufferObject getValueAsVBO() {
            return (VertexBufferObject) value;
        }

        public void setValue(Object value) {
            Object oldValue = this.value;
            this.value = value;
            needUpdate = true;

            if (oldValue.getClass() == value.getClass()) return;

            for (var type : VertexAttributeType.values()) {
                if (!type.is(value.getClass())) continue;
                this.type = type;
                return;
            }
        }

        public void apply(int index) {
            if (!needUpdate) return;
            needUpdate = false;
            if (value != null)
                type.apply(index, element, value);
            else
                type.applyDefault(index, element);
        }

        public void dispose() {
            if (value instanceof VertexBufferObject) {
                ((VertexBufferObject) value).dispose();
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private List<VertexAttribute> attributes = new ArrayList<>();

        private VertexBufferObject indices;
        private GLDataType indexType;
        private GLDrawMode drawMode = GLDrawMode.TRIANGLES;

        private int vertexCount;

        private Builder() {
        }

        public Builder newBufferAttribute(GLVertexElement element, GLBufferUsage usage) {
            attributes.add(new VertexAttribute(element, new VertexBufferObject(GLBufferType.ARRAY_BUFFER, usage)));
            return this;
        }

        public Builder newBufferAttribute(GLVertexElement element, GLBufferUsage usage, ByteBuffer buffer) {
            attributes.add(new VertexAttribute(element, new VertexBufferObject(GLBufferType.ARRAY_BUFFER, usage, buffer)));
            if (element.getUsage() == GLVertexElement.Usage.POSITION && indices != null) {
                vertexCount = buffer.limit() / element.getBytes();
            }
            return this;
        }

        public Builder newValueAttribute(GLVertexElement element, Object value) {
            attributes.add(new VertexAttribute(element, value));
            return this;
        }

        public Builder newIndicesBuffer(GLBufferUsage usage, GLDataType indexType) {
            indices = new VertexBufferObject(GLBufferType.ELEMENT_ARRAY_BUFFER, usage);
            this.indexType = indexType;
            return this;
        }

        public Builder newIndicesBuffer(GLBufferUsage usage, GLDataType indexType, ByteBuffer buffer) {
            indices = new VertexBufferObject(GLBufferType.ELEMENT_ARRAY_BUFFER, usage, buffer);
            this.indexType = indexType;
            this.vertexCount = buffer.limit() / indexType.bytes;
            return this;
        }

        public Builder newIndicesBuffer(GLBufferUsage usage, GLDataType indexType, ShortBuffer buffer) {
            indices = new VertexBufferObject(GLBufferType.ELEMENT_ARRAY_BUFFER, usage);
            indices.uploadData(buffer);
            this.indexType = indexType;
            this.vertexCount = buffer.limit() * Short.BYTES / indexType.bytes;
            return this;
        }

        public Builder newIndicesBuffer(GLBufferUsage usage, GLDataType indexType, IntBuffer buffer) {
            indices = new VertexBufferObject(GLBufferType.ELEMENT_ARRAY_BUFFER, usage);
            indices.uploadData(buffer);
            this.indexType = indexType;
            this.vertexCount = buffer.limit() * Integer.BYTES / indexType.bytes;
            return this;
        }

        public Builder drawMode(GLDrawMode drawMode) {
            this.drawMode = drawMode;
            return this;
        }

        public VertexArrayObject build() {
            VertexArrayObject vao = new VertexArrayObject();
            vao.id = GL30.glGenVertexArrays();
            vao.attributes = attributes.toArray(VertexAttribute[]::new);
            vao.drawMode = Validate.notNull(drawMode, "Draw mode cannot be null");
            vao.indices = indices;
            vao.indexType = indexType;
            vao.vertexCount = vertexCount;
            vao.refreshAttribute();
            return vao;
        }
    }
}
