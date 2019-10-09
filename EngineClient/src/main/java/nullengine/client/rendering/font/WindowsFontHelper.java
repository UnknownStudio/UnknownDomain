package nullengine.client.rendering.font;

import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import nullengine.client.rendering.gl.buffer.GLBuffer;
import nullengine.client.rendering.gl.buffer.GLBufferFormats;
import nullengine.client.rendering.gl.buffer.GLBufferMode;
import nullengine.client.rendering.texture.TextureManager;
import org.apache.commons.io.IOUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public final class WindowsFontHelper implements FontHelper {

    public static final int SUPPORTED_CHARACTER_COUNT = 0x10000;

    private static final int[] EIDs = {STBTT_MS_EID_UNICODE_BMP, STBTT_MS_EID_SHIFTJIS, STBTT_MS_EID_UNICODE_FULL, STBTT_MS_EID_SYMBOL};
    private static final int[] LANGs = {
            STBTT_MS_LANG_ENGLISH,
            STBTT_MS_LANG_CHINESE,
            STBTT_MS_LANG_DUTCH,
            STBTT_MS_LANG_FRENCH,
            STBTT_MS_LANG_GERMAN,
            STBTT_MS_LANG_HEBREW,
            STBTT_MS_LANG_ITALIAN,
            STBTT_MS_LANG_JAPANESE,
            STBTT_MS_LANG_KOREAN,
            STBTT_MS_LANG_RUSSIAN,
            STBTT_MS_LANG_SPANISH,
            STBTT_MS_LANG_SWEDISH};

    private final List<Font> availableFonts = new ArrayList<>();
    private final Table<String, String, NativeTTFontInfo> loadedFontInfos = Tables.newCustomTable(new HashMap<>(), HashMap::new);
    private final Map<Font, NativeTTFont> loadedNativeFonts = new HashMap<>();

    private Font defaultFont;

    public WindowsFontHelper() {
        initLocalFonts();
    }

    private int getLanguageId(Locale locale) {
        switch (locale.getLanguage()) {
            case "zh":
                return STBTT_MS_LANG_CHINESE;
            case "nl":
                return STBTT_MS_LANG_DUTCH;
            case "fr":
                return STBTT_MS_LANG_FRENCH;
            case "de":
                return STBTT_MS_LANG_GERMAN;
            case "it":
                return STBTT_MS_LANG_ITALIAN;
            case "ja":
                return STBTT_MS_LANG_JAPANESE;
            case "ko":
                return STBTT_MS_LANG_KOREAN;
            case "ru":
                return STBTT_MS_LANG_RUSSIAN;
            case "es":
                return STBTT_MS_LANG_SPANISH;
            case "sv":
                return STBTT_MS_LANG_SWEDISH;
            default:
                return STBTT_MS_LANG_ENGLISH;
        }
    }

    private void initLocalFonts() {
        for (Path fontFile : findLocalTTFonts()) {
            try {
                loadNativeFontInfo(fontFile);
            } catch (IOException | IllegalStateException ignored) {
            }
        }
    }

    private List<Path> findLocalTTFonts() {
        try {
            return Files.walk(Path.of("C:\\Windows\\Fonts").toAbsolutePath())
                    .filter(path -> path.getFileName().toString().endsWith(".ttf") || path.getFileName().toString().endsWith(".ttc"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public Font getDefaultFont() {
        return defaultFont;
    }

    public void setDefaultFont(Font defaultFont) {
        this.defaultFont = defaultFont;
    }

    @Override
    public List<Font> getAvailableFonts() {
        return Collections.unmodifiableList(availableFonts);
    }

    @Override
    public boolean isAvailableFont(Font font) {
        return loadedFontInfos.contains(font.getFamily(), font.getStyle());
    }

    @Override
    public Font loadFont(Path path) throws IOException {
        return loadNativeFontInfo(path).getFont();
    }

    @Override
    public Font loadFont(Path path, float size) throws IOException {
        NativeTTFontInfo nativeTTFontInfo = loadNativeFontInfo(path);
        return new Font(nativeTTFontInfo.getFont(), size);
    }

    @Override
    public Font loadFont(InputStream input) throws IOException {
        return new Font(loadNativeFontInfo(input).getFont(), 1);
    }

    @Override
    public Font loadFont(InputStream input, float size) throws IOException {
        return new Font(loadNativeFontInfo(input).getFont(), size);
    }

    @Override
    public float computeTextWidth(CharSequence text, Font font) {
        if (text == null || text.length() == 0) {
            return 0;
        }

        NativeTTFont nativeFont = getNativeFont(font);
        STBTTFontinfo info = nativeFont.getInfo().getFontInfo();
        int width = 0;

        try (MemoryStack stack = stackPush()) {
            IntBuffer pCodePoint = stack.mallocInt(1);
            IntBuffer pAdvancedWidth = stack.mallocInt(1);
            IntBuffer pLeftSideBearing = stack.mallocInt(1);

            int i = 0;
            while (i < text.length()) {
                i += getCodePoint(text, i, pCodePoint);
                int cp = pCodePoint.get(0);

                stbtt_GetCodepointHMetrics(info, cp, pAdvancedWidth, pLeftSideBearing);
                if (i < text.length()) {
                    getCodePoint(text, i, pCodePoint);
                    pAdvancedWidth.put(0, pAdvancedWidth.get(0)
                            + stbtt_GetCodepointKernAdvance(info, cp, pCodePoint.get(0)));
                }
                width += pAdvancedWidth.get(0);
            }
        }

        return width * nativeFont.getScaleForPixelHeight();
    }

    @Override
    public float computeTextHeight(CharSequence text, Font font) {
        if (text == null || text.length() == 0) {
            return 0;
        }

        var nativeTTFont = getNativeFont(font);
        STBTTBakedChar.Buffer cdata = nativeTTFont.getCharBuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer charPointBuffer = stack.mallocInt(1);
            FloatBuffer posX = stack.floats(0);
            FloatBuffer posY = stack.floats(0 + font.getSize());

            float factorX = 1.0f / nativeTTFont.getInfo().getContentScaleX();
            float factorY = 1.0f / nativeTTFont.getInfo().getContentScaleY();

            float centerY = 0 + font.getSize();

            int bitmapWidth = nativeTTFont.getBitmapWidth();
            int bitmapHeight = nativeTTFont.getBitmapHeight();
            STBTTAlignedQuad stbQuad = STBTTAlignedQuad.mallocStack(stack);
            float maxY = (float) (nativeTTFont.getInfo().getAscent() - nativeTTFont.getInfo().getDescent()) * stbtt_ScaleForPixelHeight(nativeTTFont.getInfo().getFontInfo(), font.getSize());
            for (int i = 0; i < text.length(); ) {
                i += getCodePoint(text, i, charPointBuffer);

                int charPoint = charPointBuffer.get(0);

                float centerX = posX.get(0);
                stbtt_GetBakedQuad(cdata, bitmapWidth, bitmapHeight, charPoint, posX, posY, stbQuad, true);
                float diff = /*Math.abs(stbQuad.y0() - stbQuad.y1())*/ stbQuad.y1();
                if (maxY < diff) {
                    maxY = diff;
                }
            }
            return maxY;
        }
    }

    private void bindTexture(NativeTTFont nativeTTFont) {
        glBindTexture(GL_TEXTURE_2D, nativeTTFont.getTextureId());
    }

    @Override
    public void renderText(GLBuffer buffer, CharSequence text, Font font, int color, Runnable renderer) throws UnavailableFontException {
        if (text == null || text.length() == 0) {
            return;
        }

        NativeTTFont nativeFont = getNativeFont(font);
        bindTexture(nativeFont);
        generateMesh(buffer, text, nativeFont, color);
        renderer.run();
        TextureManager.instance().getWhiteTexture().bind();
    }

    private void generateMesh(GLBuffer buffer, CharSequence text, NativeTTFont nativeTTFont, int color) {
        STBTTFontinfo fontInfo = nativeTTFont.getInfo().getFontInfo();
        float fontHeight = nativeTTFont.getFont().getSize();
        float scale = nativeTTFont.getScaleForPixelHeight();

        STBTTBakedChar.Buffer cdata = nativeTTFont.getCharBuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer charPointBuffer = stack.mallocInt(1);
            FloatBuffer posX = stack.floats(0);
            FloatBuffer posY = stack.floats(0 + fontHeight);

            float factorX = 1.0f / nativeTTFont.getInfo().getContentScaleX();
            float factorY = 1.0f / nativeTTFont.getInfo().getContentScaleY();
            factorX = 1.0f;
            factorY = 1.0f;

            float r = ((color >> 16) & 255) / 255f;
            float g = ((color >> 8) & 255) / 255f;
            float b = (color & 255) / 255f;
            float a = ((color >> 24) & 255) / 255f;

            float centerY = 0 + fontHeight;

            int bitmapWidth = nativeTTFont.getBitmapWidth();
            int bitmapHeight = nativeTTFont.getBitmapHeight();
            STBTTAlignedQuad stbQuad = STBTTAlignedQuad.mallocStack(stack);
            buffer.begin(GLBufferMode.TRIANGLES, GLBufferFormats.POSITION_COLOR_ALPHA_TEXTURE);
            for (int i = 0; i < text.length(); ) {
                i += getCodePoint(text, i, charPointBuffer);

                int charPoint = charPointBuffer.get(0);

                float centerX = posX.get(0);
                stbtt_GetBakedQuad(cdata, bitmapWidth, bitmapHeight, charPoint, posX, posY, stbQuad, true);
                posX.put(0, scale(centerX, posX.get(0), factorX));
                if (i < text.length()) {
                    getCodePoint(text, i, charPointBuffer);
                    posX.put(0, posX.get(0)
                            + stbtt_GetCodepointKernAdvance(fontInfo, charPoint, charPointBuffer.get(0)) * scale);
                }
                float x0 = scale(centerX, stbQuad.x0(), factorX), x1 = scale(centerX, stbQuad.x1(), factorX),
                        y0 = scale(centerY, stbQuad.y0(), factorY), y1 = scale(centerY, stbQuad.y1(), factorY); // FIXME: Incorrect y0
                buffer.pos(x0, y0, 0).color(r, g, b, a).uv(stbQuad.s0(), stbQuad.t0()).endVertex();
                buffer.pos(x0, y1, 0).color(r, g, b, a).uv(stbQuad.s0(), stbQuad.t1()).endVertex();
                buffer.pos(x1, y0, 0).color(r, g, b, a).uv(stbQuad.s1(), stbQuad.t0()).endVertex();

                buffer.pos(x1, y0, 0).color(r, g, b, a).uv(stbQuad.s1(), stbQuad.t0()).endVertex();
                buffer.pos(x0, y1, 0).color(r, g, b, a).uv(stbQuad.s0(), stbQuad.t1()).endVertex();
                buffer.pos(x1, y1, 0).color(r, g, b, a).uv(stbQuad.s1(), stbQuad.t1()).endVertex();
            }
        }
    }

    public NativeTTFont getNativeFont(Font font) {
        return loadedNativeFonts.computeIfAbsent(font, this::loadNativeFont);
    }

    private NativeTTFont loadNativeFont(Font font) {
        NativeTTFontInfo parent = loadedFontInfos.get(font.getFamily(), font.getStyle());
        if (parent == null) {
            throw new UnavailableFontException(font);
        }
        return loadNativeFont(parent, font);
    }

    private NativeTTFont loadNativeFont(NativeTTFontInfo info, Font font) {
        ByteBuffer fontData = info.getFontData();
        float scale = stbtt_ScaleForPixelHeight(info.getFontInfo(), font.getSize());

        int textureId = GL11.glGenTextures();
        STBTTBakedChar.Buffer charBuffer = STBTTBakedChar.malloc(SUPPORTED_CHARACTER_COUNT);
        int bitmapSize = getBitmapSize(font.getSize(), SUPPORTED_CHARACTER_COUNT);
        ByteBuffer bitmap = BufferUtils.createByteBuffer(bitmapSize * bitmapSize);
        stbtt_BakeFontBitmap(fontData, font.getSize(), bitmap, bitmapSize, bitmapSize, 0, charBuffer);

        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, bitmapSize, bitmapSize, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
        glBindTexture(GL_TEXTURE_2D, 0);

        return new NativeTTFont(info, font, textureId, charBuffer, bitmapSize, bitmapSize, scale);
    }

    private NativeTTFontInfo loadNativeFontInfo(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer fontData = ByteBuffer.allocateDirect(bytes.length).put(bytes).flip();
        var fontCount = stbtt_GetNumberOfFonts(fontData);
        if(fontCount == -1){
            throw new IllegalArgumentException(String.format("Cannot determine the number of fonts in the font file. File: %s", path));
        }
        NativeTTFontInfo parent = null;
        for (int i = 0; i < fontCount; i++) {
            STBTTFontinfo fontInfo = STBTTFontinfo.create();
            if (!stbtt_InitFont(fontInfo, fontData, stbtt_GetFontOffsetForIndex(fontData, i))) {
                throw new IllegalStateException(String.format("Failed in initializing ttf font info. File: %s", path));
            }

            int encodingId = findEncodingId(fontInfo);
            if (encodingId == -1) {
                throw new FontLoadException("Cannot load font because of not found encoding id. Path: " + path);
            }

            String family = stbtt_GetFontNameString(fontInfo, STBTT_PLATFORM_ID_MICROSOFT, encodingId, STBTT_MS_LANG_ENGLISH, 1)
                    .order(ByteOrder.BIG_ENDIAN).asCharBuffer().toString();
            String style = stbtt_GetFontNameString(fontInfo, STBTT_PLATFORM_ID_MICROSOFT, encodingId, STBTT_MS_LANG_ENGLISH, 2)
                    .order(ByteOrder.BIG_ENDIAN).asCharBuffer().toString();

            try (MemoryStack stack = stackPush()) {
                IntBuffer pAscent = stack.mallocInt(1);
                IntBuffer pDescent = stack.mallocInt(1);
                IntBuffer pLineGap = stack.mallocInt(1);

                stbtt_GetFontVMetrics(fontInfo, pAscent, pDescent, pLineGap);

                FloatBuffer p1 = stack.mallocFloat(1);
                FloatBuffer p2 = stack.mallocFloat(1);

                GLFW.glfwGetMonitorContentScale(GLFW.glfwGetPrimaryMonitor(), p1, p2);

                IntBuffer x0 = stack.mallocInt(1);
                IntBuffer y0 = stack.mallocInt(1);
                IntBuffer x1 = stack.mallocInt(1);
                IntBuffer y1 = stack.mallocInt(1);
                stbtt_GetFontBoundingBox(fontInfo, x0, y0, x1, y1);

            parent = NativeTTFontInfo.builder()
                    .fontFile(path)
                    .platformId(STBTT_PLATFORM_ID_MICROSOFT)
                    .encodingId(encodingId)
                    .languageId(STBTT_MS_LANG_ENGLISH)
                    .family(family).style(style).offsetIndex(i)
                    .ascent(pAscent.get(0)).descent(pDescent.get(0)).lineGap(pLineGap.get(0))
                    .contentScaleX(p1.get(0)).contentScaleY(p2.get(0))
                    .boundingBox(new int[]{x0.get(), y0.get(), x1.get(), y1.get()})
                    .build();
            loadedFontInfos.put(family, style, parent);
            availableFonts.add(parent.getFont());
            }
        }
        return parent;
    }

    private NativeTTFontInfo loadNativeFontInfo(InputStream input) throws IOException {
        byte[] bytes = IOUtils.toByteArray(input);
        return loadNativeFontInfo(ByteBuffer.allocateDirect(bytes.length).put(bytes).flip());
    }

    private NativeTTFontInfo loadNativeFontInfo(ByteBuffer buffer) {
        var fontCount = stbtt_GetNumberOfFonts(buffer);
        if(fontCount == -1){
            throw new IllegalArgumentException("Cannot determine the number of fonts in the font buffer.");
        }
        NativeTTFontInfo parent = null;
        for (int i = 0; i < fontCount; i++) {
            STBTTFontinfo fontInfo = STBTTFontinfo.create();
            if (!stbtt_InitFont(fontInfo, buffer)) {
                throw new IllegalStateException("Failed in initializing ttf font info");
            }

            int encodingId = findEncodingId(fontInfo);
            if (encodingId == -1) {
                throw new FontLoadException("Cannot load font because of not found encoding id.");
            }

            String family = stbtt_GetFontNameString(fontInfo, STBTT_PLATFORM_ID_MICROSOFT, encodingId, STBTT_MS_LANG_ENGLISH, 1)
                    .order(ByteOrder.BIG_ENDIAN).asCharBuffer().toString();
            String style = stbtt_GetFontNameString(fontInfo, STBTT_PLATFORM_ID_MICROSOFT, encodingId, STBTT_MS_LANG_ENGLISH, 2)
                    .order(ByteOrder.BIG_ENDIAN).asCharBuffer().toString();

            try (MemoryStack stack = stackPush()) {
                IntBuffer pAscent = stack.mallocInt(1);
                IntBuffer pDescent = stack.mallocInt(1);
                IntBuffer pLineGap = stack.mallocInt(1);

                stbtt_GetFontVMetrics(fontInfo, pAscent, pDescent, pLineGap);

                FloatBuffer p1 = stack.mallocFloat(1);
                FloatBuffer p2 = stack.mallocFloat(1);

                GLFW.glfwGetMonitorContentScale(GLFW.glfwGetPrimaryMonitor(), p1, p2);

                IntBuffer x0 = stack.mallocInt(1);
                IntBuffer y0 = stack.mallocInt(1);
                IntBuffer x1 = stack.mallocInt(1);
                IntBuffer y1 = stack.mallocInt(1);
                stbtt_GetFontBoundingBox(fontInfo, x0, y0, x1, y1);

                parent = NativeTTFontInfo.builder()
                        .fontData(buffer)
                        .fontInfo(fontInfo)
                        .platformId(STBTT_PLATFORM_ID_MICROSOFT)
                        .encodingId(encodingId)
                        .languageId(STBTT_MS_LANG_ENGLISH)
                        .family(family).style(style).offsetIndex(i)
                        .ascent(pAscent.get(0)).descent(pDescent.get(0)).lineGap(pLineGap.get(0))
                        .contentScaleX(p1.get(0)).contentScaleY(p2.get(0))
                        .boundingBox(new int[]{x0.get(), y0.get(), x1.get(), y1.get()})
                        .build();
                loadedFontInfos.put(family, style, parent);
                availableFonts.add(parent.getFont());
            }
        }
        return parent;
    }

    private int findEncodingId(STBTTFontinfo fontInfo) {
        for (int i = 0; i < EIDs.length; i++) {
            if (stbtt_GetFontNameString(fontInfo, STBTT_PLATFORM_ID_MICROSOFT, EIDs[i], STBTT_MS_LANG_ENGLISH, 1) != null) {
                return EIDs[i];
            }
        }
        return -1;
    }

    private int getBitmapSize(float size, int countOfChar) {
        return (int) Math.ceil((size + 2 * size / 16.0f) * Math.sqrt(countOfChar));
    }

    private float scale(float center, float offset, float factor) {
        return (offset - center) * factor + center;
    }

    private int getCodePoint(CharSequence text, int i, IntBuffer cpOut) {
        char c1 = text.charAt(i);
        if (Character.isHighSurrogate(c1) && i + 1 < text.length()) {
            char c2 = text.charAt(i + 1);
            if (Character.isLowSurrogate(c2)) {
                cpOut.put(0, Character.toCodePoint(c1, c2));
                return 2;
            }
        }
        cpOut.put(0, c1);
        return 1;
    }
}