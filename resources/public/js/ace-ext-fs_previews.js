// --
// FreeSpace Preview for ACE
// questions? akumpf@gmail.com
// --
// NOTE: This is an experimental extension. If you have suggestions, please share!
// --
// This extension adds a preview layer to the ace rendering stack to show
// embedded visual information in the free space below a link (if available).
// Links are assumed to be anything that the tokenizer labels as "link".
// --
// To enable FreeSpacePreviews, include this file and set the ACE editor's options:
//
//    editor.setOptions({enableFreeSpacePreviews: true});
//
// --

define("ace/ext/fs_previews", ["require","exports","module","ace/editor","ace/config"], function(require, exports, module) {

    var fsPreviewCss = "\
.ace_fs_preview {\
position: absolute;\
right: 20px;\
border-left: 2px dotted rgba(128,128,128,0.5);\
padding: 2px;\
padding-left: 7px;\
overflow: hidden;\
cursor: text;\
}\
.ace_fs_preview > * {pointer-events: auto;}\
\
.ace_fs_preview img {height: 100%; width: auto; background-color: rgba(128,128,128,0.85);}\
.ace_fs_preview img:hover {outline: 2px solid #808080;}\
\
.ace_fs_preview iframe {height: 100%; width: 100%; background: #808080;}\
";

    var dom = require("../lib/dom");
    dom.importCssString(fsPreviewCss, "ace_fs_previews");

    var Editor = require("ace/editor").Editor;
    var MAX_UNSEEN = 100; // don't immediately remove unseen previews. keep the last few around.

    function stringHashAbs(str){
        str = str || "";
        var hash = 0, i, chr, len;
        if (str.length === 0) return hash;
        for (i = 0, len = str.length; i < len; i++) {
            chr   = str.charCodeAt(i);
            hash  = ((hash << 5) - hash) + chr;
            hash |= 0; // Convert to 32bit integer
        }
        return hash < 0 ? -hash : hash;
    }

    function typeOf(url) {
        var mtype = null;
        var mres  = null;
        if (!mtype) {
            mres = url.match(/\[\*[^\]]+\]/);
            if (mres) mtype = "image-top";
        }
        if (!mtype) {
            mres = url.match(/\[-[^\]]+\]/);
            if (mres) mtype = "image-bottom";
        }
        // disabled youtube embed
        // if (!mtype) {
        //     mres = url.match(/(?:http:\/\/)?(?:www\.)?(?:youtube\.com|youtu\.be)\/(?:watch\?v=)?([^<]+)/);
        //     if (mres) mtype = "youtube";
        // }
        if (!mtype) {
            mres = url.match(/.*\.(jpg|gif|png|jpeg|ico|svg|bmp)$/);
            if (mres) mtype = "image";
        }
        return [mtype, mres];
    }

    function countBlankLines(lines) {
        var blankline_count = 0;
        var blanklines_height = 0;
        for (var i = 0; i < lines.length; i++) {
            var $next = $(lines[i]);
            if($.trim($next.text()) !== "") break;
            var line_height = $next[0].style.height;
            line_height = line_height.substring(0, line_height.length - 2);
            line_height = parseFloat(line_height);
            blanklines_height += line_height;
            blankline_count++;
        }
        return [blankline_count, blanklines_height];
    }

    function makePreview(line_numbers, $cell, $line) {
        var cell_index = $cell.index();
        var line_index = parseInt($(line_numbers[$line.index()]).text());
        var text = $cell.text();
        var preview_id = "preview_" + line_index + "_" + cell_index + "_" + stringHashAbs(text);

        var [preview_type, groups] = typeOf(text);
        if (!preview_type) return null;

        var lines;
        if (preview_type ==="image-bottom") {
            lines = $line.prevAll(".ace_line");
        } else {
            lines = $line.nextAll(".ace_line");
        }

        var [blankline_count, blanklines_height] = countBlankLines(lines);
        var url = text;
        if (preview_type === "image-top" || preview_type === "image-bottom") {
            url = text.substring(2, text.length - 1);
            if (url.indexOf(',') !== -1) {
                var [a, b] = url.split(',');
                url = a;
                blankline_count = parseInt(b);
                blanklines_height = $line[0].style.height;
                blanklines_height = blanklines_height.substring(0, blanklines_height.length - 2);
                blanklines_height = parseFloat(blanklines_height);
                blanklines_height *= blankline_count;
            }
        }

        if (blankline_count < 1) return;

        var cell_height = $line[0].style.height;
        cell_height = cell_height.substring(0, cell_height.length - 2);
        cell_height = parseFloat(cell_height);

        var y = $line.position().top + $line.parent().position().top + $cell.height() + 2;
        var x = ($cell.position().left + 6);
        var height = Math.min(900, blanklines_height - 8);
        var height_px = height + "px";
        var width_px = "auto";
        var content = "...";
        switch (preview_type) {
            case "youtube":
                content = '<iframe src="http://www.youtube.com/embed/'+groups[1]+
                    '?modestbranding=1&rel=0&wmode=transparent&theme=light&color=white"\
                     frameborder="0" allowfullscreen></iframe>';
                width_px = Math.max(120, Math.min(640, Math.ceil(parseFloat(height_px)*16.0/9.0))) + "px";
                break;
            case "image":
                content = "<a href='"+text+"' target='_blank'><img src='"+text+"'/></a>";
                break;
            case "image-top":
                content = "<a href='/file?id="+url+"' target='_blank'><img src='/file?id="+url+"'/></a>";
                break;
            case "image-bottom":
                content = "<a href='/file?id="+url+"' target='_blank'><img src='/file?id="+url+"'/></a>";
                y -= height;
                y -= cell_height;
                y -= 8;
                break;
        }

        var y_px = y + "px";
        var x_px = x + "px";
        var css = {top: y_px, left: x_px, height: height_px, width: width_px};
        var preview = "<div class='ace_fs_preview' id='"+preview_id+"' style='top: "+y_px+"; left: "+x_px+"; height: "+height_px+"; width: "+width_px+";'>"+content+"</div>";
        return {id: preview_id, css: css, html: preview};
    }

    function onAfterRender(err, renderer) {
        var $previews = $(renderer.container).find(".ace_content .ace_layer.ace_fs_previews");
        $previews.find(".ace_fs_preview").addClass("unseen");
        var line_numbers = $(renderer.container).find(".ace_gutter-cell");
        $(renderer.content).find(".ace_line .ace_link, .ace_line .ace_image").each(function(index, el){
            // the element containing the url
            var $cell = $(el);
            var $line = $cell.parents(".ace_line");
            var preview = makePreview(line_numbers, $cell, $line);
            if (preview) {
                var $existing_preview = $previews.find("#" + preview.id);
                if ($existing_preview.length === 0) {
                    $previews.prepend(preview.html);
                } else {
                    $existing_preview.css(preview.css).removeClass("unseen").show();
                }
            }
        });

        var $unseen = $previews.find(".ace_fs_preview.unseen");
        $unseen.hide();
        if ($unseen.length > MAX_UNSEEN) {
            console.log("Removing extra unseen previews:", $unseen.length-MAX_UNSEEN);
            $unseen.slice(-($unseen.length-MAX_UNSEEN)).remove();
        }
    }

    require("../config").defineOptions(Editor.prototype, "editor", {
        enableFreeSpacePreviews: {
            set: function(val) {
                if (val) {
                    console.log("FreeSpacePreviews: Enabled");
                    this.renderer.on("afterRender",onAfterRender);
                    $(this.container).find(".ace_content").append("<div class='ace_layer ace_fs_previews'></div>");
                } else {
                    console.log("FreeSpacePreviews: Disabled");
                    this.renderer.off("afterRender",onAfterRender);
                    $(this.container).find(".ace_content .ace_layer.ace_fs_previews").remove();
                }
            },
            value: false
        }
    });
});

(function() {
    window.require(["ace/ext/fs_previews"], function() {});
})();
