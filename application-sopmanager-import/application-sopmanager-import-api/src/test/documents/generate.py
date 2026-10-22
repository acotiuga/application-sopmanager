#!/usr/bin/env python3
# Generates the dummy documents used by the tests of the import of documents into SOP documents (their file names
# without umlauts nor spaces, which Git quotes; their content in German):
#   python3 generate.py ../resources/documents
# The PDF documents and the Word 97-2003 documents are exported by LibreOffice (soffice, in the PATH).
#
# The documents reproduce, with dummy content, the structures found in the real SOP documents to import:
# - a native Word document of the SOP template: the code and the title in the page header, the release details in a
#   table of the page footer;
# - a document converted from PDF (Aspose): a fixed layout where each line of text is positioned in a frame, the page
#   header and footer repeated on each page inside the content, and the borders of the tables drawn by images behind
#   the text;
# - a document whose page header and footer were rebuilt by hand inside the content: a text box with the code and the
#   title next to the logo at the top of each page, the table of the release details at the bottom of each page;
# - a clean document, without page header and footer;
# - the versions of the same document in other formats: the PDF exported from the native Word document (tagged, the
#   page header and footer marked as artifacts), the original PDF of the document converted from PDF, a Word 97-2003
#   document and the Word document converted from it;
# - files that can't be imported: a damaged document, a Word lock file, a PDF without structure, a text file.
import io
import json
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
import zlib

W_NS = ('xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" '
        'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" '
        'xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" '
        'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" '
        'xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture" '
        'xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" '
        'xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape" '
        'xmlns:v="urn:schemas-microsoft-com:vml" '
        'xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml" mc:Ignorable="w14"')
REL = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
DATE = (2026, 10, 20, 12, 0, 0)


def png(width, height, pixel):
    """A PNG image whose pixel (x, y) has the RGB color pixel(x, y)."""
    rows = b''.join(b'\0' + b''.join(bytes(pixel(x, y)) for x in range(width)) for y in range(height))

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))


# A dummy logo: a dark circle and two lines of "text".
LOGO = png(240, 60, lambda x, y: (30, 30, 30) if (x - 30) ** 2 + (y - 30) ** 2 < 400
           or (70 <= x <= 230 and (16 <= y <= 24 or 34 <= y <= 42)) else (255, 255, 255))
# A dummy photo of the content: a gradient.
PHOTO = png(160, 90, lambda x, y: (40 + x, 120 + y, 200 - x // 2))
# The borders of a table of a fixed layout, drawn by an image.
GRID = png(300, 60, lambda x, y: (150, 150, 150) if x in (0, 150, 299) or y in (0, 30, 59) else (255, 255, 255))
# The band at the top of the pages of a fixed layout: the logo and a line under it.
BAND = png(300, 30, lambda x, y: (30, 30, 30) if (x < 20 and y < 20) or y == 28 else (255, 255, 255))


def esc(text):
    return text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def run(text, bold=False, italic=False, underline=False, size=None):
    props = ''.join([
        '<w:b/>' if bold else '', '<w:i/>' if italic else '', '<w:u w:val="single"/>' if underline else '',
        f'<w:sz w:val="{size}"/>' if size else ''])
    return f'<w:r>{f"<w:rPr>{props}</w:rPr>" if props else ""}<w:t xml:space="preserve">{esc(text)}</w:t></w:r>'


def para(*runs, style=None, num=None, frame=None, jc=None, ind=None):
    props = ''.join([
        f'<w:pStyle w:val="{style}"/>' if style else '',
        f'<w:framePr w:w="{frame[2]}" w:hSpace="0" w:wrap="auto" w:vAnchor="page" w:hAnchor="page" '
        f'w:x="{frame[0]}" w:y="{frame[1]}"/>' if frame else '',
        f'<w:numPr><w:ilvl w:val="{num[1]}"/><w:numId w:val="{num[0]}"/></w:numPr>' if num else '',
        f'<w:ind w:left="{ind}"/>' if ind else '',
        f'<w:jc w:val="{jc}"/>' if jc else ''])
    content = ''.join(r if r.startswith('<') else run(r) for r in runs)
    return f'<w:p>{f"<w:pPr>{props}</w:pPr>" if props else ""}{content}</w:p>'


def cell(*paragraphs, width=2000):
    return f'<w:tc><w:tcPr><w:tcW w:w="{width}" w:type="dxa"/></w:tcPr>{"".join(paragraphs) or para()}</w:tc>'


def table(*rows):
    borders = ''.join(f'<w:{side} w:val="single" w:sz="4" w:space="0" w:color="000000"/>'
                      for side in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'))
    return (f'<w:tbl><w:tblPr><w:tblW w:w="0" w:type="auto"/><w:tblBorders>{borders}</w:tblBorders></w:tblPr>'
            + ''.join('<w:tr>' + ''.join(row) + '</w:tr>' for row in rows) + '</w:tbl>')


def field(instruction, value):
    return (f'<w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> {instruction} '
            f'</w:instrText></w:r><w:r><w:fldChar w:fldCharType="separate"/></w:r>{run(value)}'
            '<w:r><w:fldChar w:fldCharType="end"/></w:r>')


def inline_image(rid, cx, cy, number):
    return (f'<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="{cx}" cy="{cy}"/>'
            f'<wp:docPr id="{number}" name="Bild {number}"/><a:graphic><a:graphicData '
            'uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr>'
            f'<pic:cNvPr id="{number}" name="image{number}.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill>'
            f'<a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm>'
            f'<a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom>'
            '</pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>')


def anchored_image(rid, x, y, cx, cy, number):
    return (f'<w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="{number}" '
            'behindDoc="1" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/>'
            f'<wp:positionH relativeFrom="page"><wp:posOffset>{x}</wp:posOffset></wp:positionH>'
            f'<wp:positionV relativeFrom="page"><wp:posOffset>{y}</wp:posOffset></wp:positionV>'
            f'<wp:extent cx="{cx}" cy="{cy}"/><wp:wrapNone/><wp:docPr id="{number}" name="Picture {number}"/>'
            '<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic>'
            f'<pic:nvPicPr><pic:cNvPr id="{number}" name="Picture {number}"/><pic:cNvPicPr/></pic:nvPicPr>'
            f'<pic:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
            f'<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm><a:prstGeom prst="rect">'
            '<a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r>')


def text_box(number, *paragraphs):
    """A text box, written by Word both as a drawing shape and as a VML shape for older readers."""
    content = ''.join(paragraphs)
    return ('<w:r><mc:AlternateContent><mc:Choice Requires="wps"><w:drawing><wp:anchor distT="0" distB="0" distL="0" '
            f'distR="0" simplePos="0" relativeHeight="{number}" behindDoc="0" locked="0" layoutInCell="1" '
            'allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="column"><wp:posOffset>3600000'
            '</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset>'
            f'</wp:positionV><wp:extent cx="2700000" cy="500000"/><wp:wrapNone/><wp:docPr id="{number}" '
            f'name="Textfeld {number}"/><a:graphic><a:graphicData '
            'uri="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"><wps:wsp><wps:cNvSpPr txBox="1"/>'
            '<wps:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="2700000" cy="500000"/></a:xfrm><a:prstGeom prst="rect">'
            f'<a:avLst/></a:prstGeom></wps:spPr><wps:txbx><w:txbxContent>{content}</w:txbxContent></wps:txbx>'
            '<wps:bodyPr/></wps:wsp></a:graphicData></a:graphic></wp:anchor></w:drawing></mc:Choice><mc:Fallback>'
            '<w:pict><v:shape style="position:absolute;margin-left:283pt;width:212pt;height:39pt" type="#_x0000_t202">'
            f'<v:textbox><w:txbxContent>{content}</w:txbxContent></v:textbox></v:shape></w:pict></mc:Fallback>'
            '</mc:AlternateContent></w:r>')


PAGE_BREAK = '<w:p><w:r><w:br w:type="page"/></w:r></w:p>'


def section(landscape=False, header=None, footer=None, margins=True):
    size = '<w:pgSz w:w="16838" w:h="11906" w:orient="landscape"/>' if landscape else '<w:pgSz w:w="11906" w:h="16838"/>'
    refs = (f'<w:headerReference w:type="default" r:id="{header}"/>' if header else '') + (
        f'<w:footerReference w:type="default" r:id="{footer}"/>' if footer else '')
    margin = ('<w:pgMar w:top="1134" w:right="1418" w:bottom="1276" w:left="1418" w:header="709" w:footer="557" '
              'w:gutter="0"/>') if margins else ('<w:pgMar w:top="0" w:right="0" w:bottom="0" w:left="0" '
                                                  'w:header="720" w:footer="720" w:gutter="0"/>')
    return f'<w:sectPr>{refs}{size}{margin}</w:sectPr>'


STYLES = f'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles {W_NS}><w:style w:type="paragraph" w:default="1" w:styleId="Standard"><w:name w:val="Normal"/></w:style>
<w:style w:type="paragraph" w:styleId="berschrift1"><w:name w:val="heading 1"/><w:basedOn w:val="Standard"/>
<w:pPr><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="berschrift2"><w:name w:val="heading 2"/><w:basedOn w:val="Standard"/>
<w:pPr><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/><w:sz w:val="26"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Listenabsatz"><w:name w:val="List Paragraph"/><w:basedOn w:val="Standard"/></w:style>
<w:style w:type="paragraph" w:styleId="Kopfzeile"><w:name w:val="header"/><w:basedOn w:val="Standard"/></w:style>
<w:style w:type="paragraph" w:styleId="Fuzeile"><w:name w:val="footer"/><w:basedOn w:val="Standard"/></w:style>
</w:styles>'''

NUMBERING = f'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering {W_NS}>
<w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/>
<w:lvlText w:val="%1)"/></w:lvl><w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="lowerLetter"/>
<w:lvlText w:val="%2)"/></w:lvl></w:abstractNum>
<w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/>
<w:lvlText w:val="&#61623;"/></w:lvl><w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="bullet"/>
<w:lvlText w:val="o"/></w:lvl></w:abstractNum>
<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
<w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
</w:numbering>'''


def docx(path, body, parts=None, rels=None, application='Microsoft Office Word', company='GKH',
         template='Vorlage_Dokument hoch2', numbering=False, compatibility_mode=None):
    """Writes a Word document: body is the content of w:body, parts the other parts {name: content}, rels the
    relationships of the main part [(id, type, target)], compatibility_mode the version of Word whose layout the
    document keeps (below 14 for a document converted from a Word 97-2003 document)."""
    parts = dict(parts or {})
    rels = list(rels or [])
    if compatibility_mode:
        rels.append(('rIdSettings', 'settings', 'settings.xml'))
        parts['word/settings.xml'] = part('settings', '<w:compat><w:compatSetting w:name="compatibilityMode" '
                                          'w:uri="http://schemas.microsoft.com/office/word" '
                                          f'w:val="{compatibility_mode}"/></w:compat>')
    rels.append(('rIdStyles', 'styles', 'styles.xml'))
    parts['word/styles.xml'] = STYLES
    if numbering:
        rels.append(('rIdNumbering', 'numbering', 'numbering.xml'))
        parts['word/numbering.xml'] = NUMBERING
    parts['word/document.xml'] = (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<w:document {W_NS}>'
                                  f'<w:body>{body}</w:body></w:document>')
    parts['word/_rels/document.xml.rels'] = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        + ''.join(f'<Relationship Id="{i}" Type="{REL}/{t}" Target="{target}"/>' for i, t, target in rels)
        + '</Relationships>')
    parts['_rels/.rels'] = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        f'<Relationship Id="rId1" Type="{REL}/officeDocument" Target="word/document.xml"/>'
        '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/'
        'core-properties" Target="docProps/core.xml"/>'
        f'<Relationship Id="rId3" Type="{REL}/extended-properties" Target="docProps/app.xml"/></Relationships>')
    parts['docProps/app.xml'] = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<Properties xmlns='
        '"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">'
        f'<Template>{template}</Template><Application>{application}</Application><Company>{company}</Company>'
        '<AppVersion>16.0000</AppVersion></Properties>')
    parts['docProps/core.xml'] = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<cp:coreProperties xmlns:cp='
        '"http://schemas.openxmlformats.org/package/2006/metadata/core-properties" '
        'xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:creator>Dummy</dc:creator>'
        '<dcterms:created xsi:type="dcterms:W3CDTF">2026-10-20T10:00:00Z</dcterms:created></cp:coreProperties>')
    overrides = {
        'word/document.xml': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml',
        'word/styles.xml': 'application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml',
        'word/numbering.xml': 'application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml',
        'word/settings.xml': 'application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml',
        'docProps/core.xml': 'application/vnd.openxmlformats-package.core-properties+xml',
        'docProps/app.xml': 'application/vnd.openxmlformats-officedocument.extended-properties+xml'}
    for name in parts:
        if name.startswith('word/header'):
            overrides[name] = 'application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml'
        elif name.startswith('word/footer') and name.endswith('.xml'):
            overrides[name] = 'application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml'
    parts['[Content_Types].xml'] = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/>'
        + ''.join(f'<Override PartName="/{name}" ContentType="{kind}"/>' for name, kind in overrides.items()
                  if name in parts) + '</Types>')
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as package:
        for name in ['[Content_Types].xml'] + sorted(n for n in parts if n != '[Content_Types].xml'):
            info = zipfile.ZipInfo(name, DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            content = parts[name]
            package.writestr(info, content.encode('utf-8') if isinstance(content, str) else content)


def part(root, content):
    return f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<w:{root} {W_NS}>{content}</w:{root}>'


def part_rels(*rels):
    return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            + ''.join(f'<Relationship Id="{i}" Type="{REL}/{t}" Target="{target}"/>' for i, t, target in rels)
            + '</Relationships>')


def export(source, target, export_filter):
    """Exports a document with LibreOffice, e.g. to a tagged PDF or a Word 97-2003 document."""
    with tempfile.TemporaryDirectory() as work:
        profile = 'file://' + os.path.join(work, 'profile')
        subprocess.run(['soffice', f'-env:UserInstallation={profile}', '--headless', '--convert-to', export_filter,
                        '--outdir', work, source], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        extension = export_filter.split(':')[0]
        shutil.move(os.path.join(work, os.path.splitext(os.path.basename(source))[0] + '.' + extension), target)


def export_pdf(source, target, tagged=True):
    options = {'UseTaggedPDF': {'type': 'boolean', 'value': 'true' if tagged else 'false'}}
    export(source, target, 'pdf:writer_pdf_Export:' + json.dumps(options))


SCOPE = lambda area, group, goal: table(  # noqa: E731
    [cell(para(run('Geltungsbereich', size=21))), cell(para(run(area, size=21)), width=7000)],
    [cell(para(run('Berufsgruppe', size=21))), cell(para(run(group, size=21)), width=7000)],
    [cell(para(run('Ziel', size=21))), cell(para(run(goal, size=21)), width=7000)])


def release_table(values, labels=('Freigabe am', 'Freigabe durch', 'Überarbeitet von', 'Revision',
                                  'Revision geplant', 'Seite'), page='1', pages='1'):
    small = lambda text: run(text, size=14)  # noqa: E731
    return table([cell(para(small(label)), width=1600) for label in labels],
                 [cell(para(small(value)), width=1600) for value in values]
                 + [cell(para(small('Seite '), field('PAGE', page), small(' / '), field('NUMPAGES', pages)))])


def native(directory):
    """A document of the SOP template of Word: the code and title in the page header, the release details in the
    page footer."""
    header = part('hdr', table([cell(para(run('ANA_AA_9.101', size=20)),
                                     para(run('Dummy Händedesinfektion', bold=True, size=28)), width=6000),
                                cell(para(inline_image('rIdLogo', 1800000, 450000, 1), jc='right'), width=3000)])
                  + para(style='Kopfzeile'))
    footer = part('ftr', para() + release_table(['05.11.2025', 'Dr. M. Muster', 'K. Beispiel', '05', '04.11.2028'])
                  + para(style='Fuzeile'))
    body = (SCOPE('Alle Stationen', 'Pflegerische und ärztliche Mitarbeiter', 'Vermeidung von Infektionen')
            + para()
            + para(run('Vorgehen', bold=True))
            + para('Die Hände werden vor und nach jedem Patientenkontakt desinfiziert.', num=(1, 0))
            + para('Das Desinfektionsmittel wird 30 Sekunden ', run('vollständig', bold=True), ' eingerieben.',
                   num=(1, 0))
            + para('Die Fingerkuppen und Daumen werden ', run('besonders', italic=True), ' beachtet.', num=(1, 1))
            + para('Schmuck und Uhren werden vor Dienstbeginn abgelegt.', num=(1, 0))
            # A paragraph indented as the text of the items.
            + para('Die Hände trocknen an der Luft.', ind=709)
            + para(run('Material', bold=True))
            + para('Händedesinfektionsmittel', num=(2, 0))
            + para('Hautpflegemittel', num=(2, 0))
            + para(run('Dosierung', bold=True))
            + table([cell(para(run('Situation', bold=True))), cell(para(run('Menge', bold=True)))],
                    [cell(para('Hygienische Händedesinfektion')), cell(para('3 ml'))],
                    [cell(para('Chirurgische Händedesinfektion')), cell(para('2 x 5 ml'))])
            + para()
            + para(inline_image('rIdPhoto', 1600000, 900000, 2))
            + para('Weitere Hinweise im ', run('Hygieneplan', underline=True), '.')
            + section(header='rIdHeader', footer='rIdFooter'))
    docx(os.path.join(directory, 'ANA_AA_9.101_Dummy-Haendedesinfektion.docx'), body, numbering=True, parts={
        'word/header1.xml': header, 'word/_rels/header1.xml.rels': part_rels(('rIdLogo', 'image', 'media/logo.png')),
        'word/footer1.xml': footer, 'word/media/logo.png': LOGO, 'word/media/photo.png': PHOTO},
        rels=[('rIdHeader', 'header', 'header1.xml'), ('rIdFooter', 'footer', 'footer1.xml'),
              ('rIdPhoto', 'image', 'media/photo.png')])
    # The PDF exported from the Word document: the same document, as tagged PDF.
    export_pdf(os.path.join(directory, 'ANA_AA_9.101_Dummy-Haendedesinfektion.docx'),
               os.path.join(directory, 'ANA_AA_9.101_Dummy-Haendedesinfektion.pdf'))


def converted(directory):
    """A document converted from PDF: every line of text in a positioned frame, the page header and footer repeated
    inside the content of each page, the borders of the tables drawn by images behind the text."""
    def text(x, y, w, value, bold=False, size=20):
        return para(run(value, bold=bold, size=size), frame=(x, y, w))

    def page(number, content, images):
        return (para(run(' ', size=2))
                + text(11985, 718, 3722, 'ANA_AA_9.102') + text(11985, 718, 3722, 'Dummy Bestückung Notfallwagen',
                                                                 bold=True, size=24)
                + content
                + ''.join(text(x, 10684, w, label, size=14) + text(x, 10684, w, value, size=14) for x, w, label, value
                          in [(1495, 1033, 'Freigabe am:', '23.01.2024'), (3682, 1189, 'Freigabe durch:', 'Dr. M. Muster'),
                              (7155, 901, 'Erstellt von', 'K. Beispiel'), (10069, 746, 'Revision', '2'),
                              (12160, 1242, 'Revision geplant', '23.01.2027'),
                              (14500, 839, 'Seite', f'Seite {number} / 2')])
                + text(1133, 11025, 7773, 'W:\\Gemeinsam\\QM\\Dokumentenlenkung\\Dokumente\\'
                                          'ANA_AA_9.102_Dummy-Bestueckung-Notfallwagen.doc', size=14)
                + '<w:p>' + images + '</w:p>')

    first = (text(1133, 1838, 1778, 'Geltungsbereich', size=21) + text(1133, 1838, 1778, 'Berufsgruppe', size=21)
             + text(1133, 1838, 1778, 'Ziel', size=21) + text(3284, 1838, 1285, 'Anästhesie', size=21)
             + text(3284, 2210, 1402, 'Pflegedienst', size=21)
             + text(3284, 2582, 6698, 'Vollständige Bestückung der Notfallwagen', size=21)
             + text(7304, 3887, 2069, 'Schublade 1', bold=True)
             + text(1844, 4245, 1450, '2 Amp. Adrenalin') + text(4455, 4245, 1617, '2 Amp. Atropin')
             + text(7491, 4245, 1837, '5 x Kanülen 20G'))
    second = (text(7633, 1875, 1416, 'Schublade 2', bold=True)
              + text(3207, 2233, 3056, '10 x Spritzen 10 ml') + text(10607, 2233, 2716, '5 x Spritzen 5 ml')
              + text(10607, 2233, 2716, '5 x Spritzen 2 ml'))
    band = lambda n: anchored_image('rIdBand', 360680, 280670, 9516745, 749935, n)  # noqa: E731
    grid = lambda n: anchored_image('rIdGrid', 613410, 1148080, 9215120, 1371600, n)  # noqa: E731
    body = (page(1, first, band(1) + grid(2)) + PAGE_BREAK + page(2, second, band(3) + grid(4))
            + section(landscape=True, margins=False))
    docx(os.path.join(directory, 'ANA_AA_9.102_Dummy-Bestueckung-Notfallwagen.docx'), body,
         parts={'word/media/band.png': BAND, 'word/media/grid.png': GRID},
         rels=[('rIdBand', 'image', 'media/band.png'), ('rIdGrid', 'image', 'media/grid.png')],
         application='Aspose', company='Aspose', template='Normal')
    # The original PDF, exported from a Word document of the SOP template that is not in the folder.
    header = part('hdr', para(run('ANA_AA_9.102', size=20), jc='right')
                  + para(run('Dummy Bestückung Notfallwagen', bold=True, size=24), jc='right'))
    footer = part('ftr', release_table(['23.01.2024', 'Dr. M. Muster', 'K. Beispiel', '2', '23.01.2027'],
                                       labels=('Freigabe am:', 'Freigabe durch:', 'Erstellt von', 'Revision',
                                               'Revision geplant', 'Seite'), pages='2')
                  + para(run('W:\\Gemeinsam\\QM\\Dokumentenlenkung\\Dokumente\\'
                             'ANA_AA_9.102_Dummy-Bestueckung-Notfallwagen.doc', size=14)))
    drawer = lambda title, *rows: (table([cell(para(run(title, bold=True)), width=9000)])  # noqa: E731
                                   + table(*[[cell(para(value), width=3000) for value in row] for row in rows]))
    body = (SCOPE('Anästhesie', 'Pflegedienst', 'Vollständige Bestückung der Notfallwagen') + para()
            + drawer('Schublade 1', ['2 Amp. Adrenalin', '2 Amp. Atropin', '5 x Kanülen 20G']) + PAGE_BREAK
            + drawer('Schublade 2', ['10 x Spritzen 10 ml', '5 x Spritzen 5 ml'], ['', '5 x Spritzen 2 ml'])
            + section(landscape=True, header='rIdHeader', footer='rIdFooter'))
    with tempfile.TemporaryDirectory() as work:
        source = os.path.join(work, 'ANA_AA_9.102_Dummy-Bestueckung-Notfallwagen.docx')
        docx(source, body, parts={'word/header1.xml': header, 'word/footer1.xml': footer},
             rels=[('rIdHeader', 'header', 'header1.xml'), ('rIdFooter', 'footer', 'footer1.xml')])
        export_pdf(source, os.path.join(directory, 'ANA_AA_9.102_Dummy-Bestueckung-Notfallwagen.pdf'))


def in_content(directory):
    """A document whose page header and footer were rebuilt inside the content: the logo and a text box with the code
    and title at the top of each page, the table of the release details at the bottom of each page."""
    def header(number):
        return para(inline_image('rIdLogo', 1800000, 450000, number),
                    text_box(number + 100, para(run('ANA_AA_9.103', bold=True, size=22)),
                             para(run('Dummy Check Arbeitsplatz', bold=True, size=22))))

    def footer(number):
        return (release_table(['12.05.2026', 'Dr. M. Muster', 'K. Beispiel', '0', '12.05.2029'],
                              labels=('Freigabe am:', 'Freigabe durch:', 'Erstellt von', 'Revision',
                                      'Revision geplant', 'Seite'), page=str(number), pages='2')
                + para(run('W:\\Gemeinsam\\QM\\Dokumentenlenkung\\Dokumente\\ANA_AA_9.103_Dummy-Check-'
                           'Arbeitsplatz.doc', size=14)))

    body = (header(1) + para()
            + SCOPE('Zentral-OP', 'Anästhesie – Pflege', 'Einsatzbereite Arbeitsplätze')
            + para(run('Durchzuführende Maßnahmen', bold=True))
            + table([cell(para('Tägliche Kontrolle des Narkosegerätes')), cell(para('Pflege'))],
                    [cell(para('Kontrolle der Medikamente')), cell(para('Pflege'))])
            + footer(1) + PAGE_BREAK
            + header(3) + para()
            + para(run('Dokumentation', bold=True))
            + para('Die Kontrolle wird in der Checkliste dokumentiert.')
            + footer(2)
            + section(landscape=False))
    docx(os.path.join(directory, 'ANA_AA_9.103_Dummy-Check-Arbeitsplatz.docx'), body,
         parts={'word/media/logo.png': LOGO}, rels=[('rIdLogo', 'image', 'media/logo.png')], template='Normal')


def clean(directory):
    """A document without page header and footer."""
    body = (para('Dummy Hausordnung', style='berschrift1')
            + para('Diese Hausordnung gilt für alle Mitarbeiter.')
            + para('Ruhezeiten', style='berschrift2')
            + para('Die Ruhezeiten werden von ', run('22 Uhr', bold=True), ' bis ', run('6 Uhr', bold=True),
                   ' eingehalten.')
            + section())
    docx(os.path.join(directory, 'Dummy-Hausordnung.docx'), body, template='Normal', company='Dummy')


def word97(directory):
    """A Word 97-2003 document, the original, and the Word document converted from it by Word: same content, the
    path of the original document in the page footer, the layout of Word 2007 kept."""
    def write(path, original_path):
        header = part('hdr', para(run('ANA_AA_9.105', size=20)) + para(run('Dummy Altformat', bold=True, size=28)))
        footer = part('ftr', release_table(['02.03.2015', 'Dr. M. Muster', 'K. Beispiel', '07', '02.03.2030'])
                      + (para(run(original_path, size=14)) if original_path else ''))
        body = (SCOPE('Alle Stationen', 'Pflegedienst', 'Einheitliche Übergabe')
                + para(run('Übergabe', bold=True))
                + para('Die Übergabe erfolgt am Bett des Patienten.', num=(1, 0))
                + para('Offene Aufgaben werden schriftlich übergeben.', num=(1, 0))
                + section(header='rIdHeader', footer='rIdFooter'))
        docx(path, body, numbering=True, parts={'word/header1.xml': header, 'word/footer1.xml': footer},
             rels=[('rIdHeader', 'header', 'header1.xml'), ('rIdFooter', 'footer', 'footer1.xml')],
             compatibility_mode=None if original_path is None else 12)

    with tempfile.TemporaryDirectory() as work:
        source = os.path.join(work, 'ANA_AA_9.105_Dummy-Altformat.docx')
        write(source, None)
        export(source, os.path.join(directory, 'ANA_AA_9.105_Dummy-Altformat.doc'), 'doc:MS Word 97')
    write(os.path.join(directory, 'ANA_AA_9.105_Dummy-Altformat.docx'),
          'W:\\Gemeinsam\\QM\\Dokumentenlenkung\\Dokumente\\ANA_AA_9.105_Dummy-Altformat.doc')


def others(directory):
    """Files that can't be imported."""
    def write(name, content):
        with open(os.path.join(directory, name), 'wb') as file:
            file.write(content)

    # A damaged document: a ZIP archive cut in the middle.
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as package:
        package.writestr(zipfile.ZipInfo('word/document.xml', DATE), '<w:document/>' * 200)
    write('ANA_AA_9.104_Dummy-beschaedigt.docx', buffer.getvalue()[:120])
    # The lock file created by Word next to an open document: the user name, padded.
    write('~$A_AA_9.101_Dummy-Haendedesinfektion.docx', b'\x05Dummy'.ljust(162, b'\0'))
    # A PDF without structure, as a scan.
    with tempfile.TemporaryDirectory() as work:
        source = os.path.join(work, 'ANA_AA_9.106_Dummy-Scan.docx')
        docx(source, para('Dummy Scan') + para('Eingescannte Arbeitsanweisung.') + section(), template='Normal')
        export_pdf(source, os.path.join(directory, 'ANA_AA_9.106_Dummy-Scan.pdf'), tagged=False)
    write('Notizen.txt', 'Dummy notes, not a SOP.\n'.encode('utf-8'))


if __name__ == '__main__':
    target = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', 'resources',
                                                                 'documents')
    os.makedirs(target, exist_ok=True)
    for generate in (native, converted, in_content, clean, word97, others):
        generate(target)
