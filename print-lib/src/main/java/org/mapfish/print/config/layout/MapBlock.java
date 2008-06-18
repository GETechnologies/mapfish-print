package org.mapfish.print.config.layout;

import com.lowagie.text.Chunk;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import org.json.JSONException;
import org.json.JSONWriter;
import org.mapfish.print.InvalidJsonValueException;
import org.mapfish.print.PDFCustomBlocks;
import org.mapfish.print.PDFUtils;
import org.mapfish.print.RenderingContext;
import org.mapfish.print.Transformer;
import org.mapfish.print.map.MapChunkDrawer;
import org.mapfish.print.utils.PJsonArray;
import org.mapfish.print.utils.PJsonObject;

public class MapBlock extends Block {
    private int spacingAfter = 0;
    private int height = 453;
    private int width = 340;
    private int absoluteX = Integer.MIN_VALUE;
    private int absoluteY = Integer.MIN_VALUE;
    private double overviewMap = Double.NaN;

    public void render(PJsonObject params, PdfElement target, RenderingContext context) throws DocumentException {
        Transformer transformer = createTransformer(context, params);

        final MapChunkDrawer drawer = new MapChunkDrawer(transformer, overviewMap, params, context, getBackgroundColor());

        if (isAbsolute()) {
            context.getCustomBlocks().addAbsoluteDrawer(new PDFCustomBlocks.AbsoluteDrawer() {
                public void render(PdfContentByte dc) {
                    final Rectangle rectangle = new Rectangle(absoluteX, absoluteY - height, absoluteX + width, absoluteY);
                    drawer.render(rectangle, dc);
                }
            });
        } else {
            //create an empty image just for reserving the room for the map
            Image background = PDFUtils.createEmptyImage(transformer.getPaperW(), transformer.getPaperH());

            Chunk mapChunk = new Chunk(background, 0f, 0f, true);

            //register a drawer that will do the job once the position of the map is known
            context.getCustomBlocks().addChunkDrawer(mapChunk, drawer);

            final Paragraph mapParagraph = new Paragraph(mapChunk);
            mapParagraph.setAlignment(Paragraph.ALIGN_CENTER);
            mapParagraph.setSpacingAfter(spacingAfter);
            target.add(mapParagraph);
        }
    }

    public Transformer createTransformer(RenderingContext context, PJsonObject params) {
        Integer dpi = params.optInt("dpi");
        if(dpi==null) {
            dpi=context.getGlobalParams().getInt("dpi");
        }
        if (!context.getConfig().getDpis().contains(dpi)) {
            throw new InvalidJsonValueException(params, "dpi", dpi);
        }

        final PJsonArray center = params.getJSONArray("center");
        final PJsonObject parent = (PJsonObject) params.getParent().getParent();
        String units = parent.getString("units");
        return new Transformer(center.getFloat(0), center.getFloat(1), width, height, params.getInt("scale"), dpi, units);
    }

    public void setSpacingAfter(int spacingAfter) {
        this.spacingAfter = spacingAfter;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    boolean isAbsolute() {
        return absoluteX != Integer.MIN_VALUE &&
                absoluteY != Integer.MIN_VALUE;
    }

    public void setAbsoluteX(int absoluteX) {
        this.absoluteX = absoluteX;
    }

    public void setAbsoluteY(int absoluteY) {
        this.absoluteY = absoluteY;
    }

    public MapBlock getMap() {
        return Double.isNaN(overviewMap) ? this : null;
    }

    public void printClientConfig(JSONWriter json) throws JSONException {
        json.object();
        json.key("width").value(width);
        json.key("height").value(height);
        json.endObject();
    }

    public void setOverviewMap(double overviewMap) {
        this.overviewMap = overviewMap;
    }
}
