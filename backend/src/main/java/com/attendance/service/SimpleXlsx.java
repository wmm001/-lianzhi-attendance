package com.attendance.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Minimal OOXML workbook. All external values are literal strings, never formulas. */
public final class SimpleXlsx {
    private SimpleXlsx() {}

    public static byte[] write(List<List<String>> rows) {
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(ZipOutputStream zip=new ZipOutputStream(bytes)) {
                entry(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
                entry(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
                entry(zip,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"考勤明细\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
                entry(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
                StringBuilder sheet=new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"10\" width=\"22\" customWidth=\"1\"/></cols><sheetData>");
                int number=0;
                for(List<String> row:rows){sheet.append("<row r=\"").append(++number).append("\">");int col=0;for(String value:row)sheet.append("<c r=\"").append(columnName(col++)).append(number).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(value)).append("</t></is></c>");sheet.append("</row>");}
                sheet.append("</sheetData><autoFilter ref=\"A1:J").append(number).append("\"/></worksheet>");
                entry(zip,"xl/worksheets/sheet1.xml",sheet.toString());
            }
            return bytes.toByteArray();
        }catch(IOException e){throw new IllegalStateException("生成考勤表失败",e);}
    }

    /**
     * 读取第一个工作表，按单元格坐标还原列位置（空单元格返回空字符串）。
     * 支持共享字符串、inlineStr 与数值单元格；不计算公式。
     */
    public static List<List<String>> read(InputStream in) {
        try(ZipInputStream zip=new ZipInputStream(in, StandardCharsets.UTF_8)) {
            String sharedXml=null;
            String sheetXml=null;
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                String name=entry.getName();
                if(name.equals("xl/sharedStrings.xml")) sharedXml=readString(zip);
                else if(name.startsWith("xl/worksheets/")&&name.endsWith(".xml")&&sheetXml==null) sheetXml=readString(zip);
            }
            if(sheetXml==null) throw new IllegalArgumentException("xlsx中未找到工作表");
            DocumentBuilderFactory factory=secureFactory();
            DocumentBuilder builder=factory.newDocumentBuilder();
            List<String> shared=sharedXml==null?Collections.emptyList()
                    :parseShared(builder.parse(new InputSource(new StringReader(sharedXml))));
            Document sheet=builder.parse(new InputSource(new StringReader(sheetXml)));
            List<List<String>> rows=new ArrayList<>();
            NodeList rowNodes=sheet.getElementsByTagName("row");
            for(int i=0;i<rowNodes.getLength();i++) {
                Element rowEl=(Element)rowNodes.item(i);
                NodeList cells=rowEl.getElementsByTagName("c");
                int lastCol=-1;
                List<String> row=new ArrayList<>();
                for(int j=0;j<cells.getLength();j++) {
                    Element cell=(Element)cells.item(j);
                    int colIndex;
                    String ref=cell.getAttribute("r");
                    if(ref!=null&&!ref.isEmpty()) colIndex=columnIndex(ref);
                    else colIndex=lastCol+1;
                    while(row.size()<=colIndex) row.add("");
                    row.set(colIndex, cellValue(cell, shared));
                    lastCol=colIndex;
                }
                rows.add(row);
            }
            return rows;
        }catch(IllegalArgumentException e) {
            throw e;
        }catch(Exception e) {
            throw new IllegalArgumentException("Excel文件解析失败，请确认上传的是内容有效的 .xlsx 文件", e);
        }
    }

    private static List<String> parseShared(Document doc) {
        List<String> shared=new ArrayList<>();
        NodeList siNodes=doc.getElementsByTagName("si");
        for(int i=0;i<siNodes.getLength();i++) {
            StringBuilder text=new StringBuilder();
            NodeList texts=((Element)siNodes.item(i)).getElementsByTagName("t");
            for(int j=0;j<texts.getLength();j++) text.append(texts.item(j).getTextContent());
            shared.add(text.toString());
        }
        return shared;
    }

    private static String cellValue(Element cell, List<String> shared) {
        String type=cell.getAttribute("t");
        if("inlineStr".equals(type)) {
            NodeList texts=cell.getElementsByTagName("t");
            return texts.getLength()==0?"":texts.item(0).getTextContent();
        }
        NodeList values=cell.getElementsByTagName("v");
        if(values.getLength()==0) return "";
        String raw=values.item(0).getTextContent();
        if("s".equals(type)) {
            try{return shared.get(Integer.parseInt(raw.trim()));}
            catch(Exception e){return "";}
        }
        return raw;
    }

    private static String readString(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[4096];
        int n;
        while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static DocumentBuilderFactory secureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setNamespaceAware(false);
        return factory;
    }

    /** 由单元格引用（如 B12）计算从 0 开始的列下标。 */
    static int columnIndex(String ref) {
        int index=0;
        for(int i=0;i<ref.length();i++) {
            char c=ref.charAt(i);
            if(c>='A'&&c<='Z') index=index*26+(c-'A'+1);
            else if(c>='a'&&c<='z') index=index*26+(c-'a'+1);
            else break;
        }
        return index-1;
    }

    private static String columnName(int index) {
        StringBuilder name=new StringBuilder();
        int n=index+1;
        while(n>0){n--;name.insert(0,(char)('A'+n%26));n/=26;}
        return name.toString();
    }

    private static String escape(String s){return s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private static void entry(ZipOutputStream zip,String name,String xml)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+xml).getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
}
