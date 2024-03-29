define("ace/theme/monokai",["require","exports","module","ace/lib/dom"], function(require, exports, module) {

exports.isDark = true;
exports.cssClass = "ace-monokai";
exports.cssText = ".ace-monokai .ace_gutter {\
background: #2F3129;\
color: #8F908A\
}\
.ace-monokai .ace_print-margin {\
width: 1px;\
background: #555651\
}\
.ace-monokai {\
background-color: #272822;\
color: #F8F8F2\
}\
.ace-monokai .ace_cursor {\
color: #F8F8F0\
}\
.ace-monokai .ace_marker-layer .ace_selection {\
background: #49483E\
}\
.ace-monokai.ace_multiselect .ace_selection.ace_start {\
box-shadow: 0 0 3px 0px #272822;\
}\
.ace-monokai .ace_marker-layer .ace_step {\
background: rgb(102, 82, 0)\
}\
.ace-monokai .ace_marker-layer .ace_bracket {\
margin: -1px 0 0 -1px;\
border: 1px solid #49483E\
}\
.ace-monokai .ace_marker-layer .ace_active-line {\
background: #202020\
}\
.ace-monokai .ace_gutter-active-line {\
background-color: #272727\
}\
.ace-monokai .ace_marker-layer .ace_selected-word {\
border: 1px solid #49483E\
}\
.ace-monokai .ace_invisible {\
color: #52524d\
}\
.ace-monokai .ace_entity.ace_name.ace_tag,\
.ace-monokai .ace_keyword,\
.ace-monokai .ace_meta.ace_tag,\
.ace-monokai .ace_storage {\
color: #F92672\
}\
.ace-monokai .ace_punctuation,\
.ace-monokai .ace_punctuation.ace_tag {\
color: #fff\
}\
.ace-monokai .ace_constant.ace_character,\
.ace-monokai .ace_constant.ace_language,\
.ace-monokai .ace_constant.ace_numeric,\
.ace-monokai .ace_constant.ace_other {\
color: #AE81FF\
}\
.ace-monokai .ace_invalid {\
color: #F8F8F0;\
background-color: #F92672\
}\
.ace-monokai .ace_invalid.ace_deprecated {\
color: #F8F8F0;\
background-color: #AE81FF\
}\
.ace-monokai .ace_support.ace_constant,\
.ace-monokai .ace_support.ace_function {\
color: #66D9EF\
}\
.ace-monokai .ace_fold {\
background-color: #A6E22E;\
border-color: #F8F8F2\
}\
.ace-monokai .ace_storage.ace_type,\
.ace-monokai .ace_support.ace_class,\
.ace-monokai .ace_support.ace_type {\
font-style: italic;\
color: #66D9EF\
}\
.ace-monokai .ace_entity.ace_name.ace_function,\
.ace-monokai .ace_entity.ace_other,\
.ace-monokai .ace_entity.ace_other.ace_attribute-name,\
.ace-monokai .ace_variable {\
color: #A6E22E\
}\
.ace-monokai .ace_variable.ace_parameter {\
font-style: italic;\
color: #FD971F\
}\
.ace-monokai .ace_string {\
color: #E6DB74\
}\
.ace-monokai .ace_comment {\
color: #75715E\
}\
.ace-monokai .ace_indent-guide {\
background: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAACCAYAAACZgbYnAAAAEklEQVQImWPQ0FD0ZXBzd/wPAAjVAoxeSgNeAAAAAElFTkSuQmCC) right repeat-y\
}\
\
/* ----------- [CUSTOM] ---------- */\
@keyframes blinker {\
  50% {\
    opacity: 0;\
  }\
}\
.ace-monokai .ace_tag.ace_declaration {\
font-weight: bold;\
color: #3DA1D2;\
/*animation: blinker 1s linear infinite;*/\
}\
.ace-monokai .ace_tag.ace_declaration.ace_highlight {\
font-weight: normal;\
color: #00ccff;\
/*animation: blinker 1s linear infinite;*/\
}\
.ace-monokai .ace_tag.ace_definition {\
font-weight: bold;\
color: #3DA1D2;\
/*animation: blinker 1s linear infinite;*/\
}\
.ace-monokai .ace_tag.ace_declaration.ace_global {\
font-weight: bold;\
color: #3DA1D2;\
text-shadow: 0px 0px 3px #3DA1D2;\
}\
.ace-monokai .ace_tag.ace_definition.ace_global {\
font-weight: bold;\
color: #3DA1D2;\
text-shadow: 0px 0px 3px #3DA1D2;\
}\
.ace-monokai .ace_tag.ace_reference {\
font-weight: bold;\
color: #dbb50f;\
/*animation: blinker 1s linear infinite;*/\
}\
.ace-monokai .ace_image {\
font-weight: bold;\
color: #00AA00;\
/*animation: blinker 1s linear infinite;*/\
}\
.ace-monokai .ace_emphasis {\
opacity: 1;\
font-weight: bold;\
}\
.ace-monokai .ace_ntag {\
font-weight: bold;\
color: #f442d1\
}\
.ace-monokai .ace_markup.ace_list {\
color: #777;\
font-weight: bold;\
}\
.ace-monokai .ace_support.ace_function {\
opacity: 0.8;\
}\
.ace-monokai .ace_support.ace_function.ace_inline {\
opacity: 1;\
color: #CC7832;\
color: #FF983C;\
}\
.ace-monokai .ace_keyword,\
.ace-monokai .ace_constant,\
.ace-monokai .ace_storage,\
.ace-monokai .ace_paren,\
.ace-monokai .ace_punctuation,\
.ace-monokai .ace_string,\
.ace-monokai .ace_identifier,\
.ace-monokai .ace_comment.ace_doc\
{\
opacity: 0.8;\
}\
.ace-monokai .ace_comment {\
}\
.ace-monokai .ace_timestamp {\
color: #48f442;\
font-weight: bold;\
}\
.ace-monokai .ace_ritsu.ace_amount {\
color: #48f442;\
font-weight: bold;\
}\
.ace-monokai .ace_ritsu.ace_pass {\
color: #48f442;\
font-weight: bold;\
}\
.ace-monokai .ace_ritsu.ace_fail {\
color: red;\
font-weight: bold;\
}\
.ace-monokai .ace_list.ace_a { color: #CC7832; }\
.ace-monokai .ace_list.ace_b { color: #AB51BA; }\
.ace-monokai .ace_list.ace_c { color: #0F9795; }\
.ace-monokai .ace_list.ace_d { color: #C93B48; }\
.ace-monokai .ace_list.ace_e { color: #629755; }\
.ace-monokai .ace_duration { color: #dbb50f; font-weight: bold; }\
\
\
"
;

var dom = require("../lib/dom");
dom.importCssString(exports.cssText, exports.cssClass);
});                (function() {
                    window.require(["ace/theme/monokai"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
            