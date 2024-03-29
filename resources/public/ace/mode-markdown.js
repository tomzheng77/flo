define("ace/mode/doc_comment_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;

var DocCommentHighlightRules = function() {
    this.$rules = {
        "start" : [ {
            token : "comment.doc.tag",
            regex : "@[\\w\\d_]+" // TODO: fix email addresses
        }, 
        DocCommentHighlightRules.getTagRule(),
        {
            defaultToken : "comment.doc",
            caseInsensitive: true
        }]
    };
};

oop.inherits(DocCommentHighlightRules, TextHighlightRules);

DocCommentHighlightRules.getTagRule = function(start) {
    return {
        token : "comment.doc.tag.storage.type",
        regex : "\\b(?:TODO|FIXME|XXX|HACK)\\b"
    };
};

DocCommentHighlightRules.getStartRule = function(start) {
    return {
        token : "comment.doc", // doc comment
        regex : "\\/\\*(?=\\*)",
        next  : start
    };
};

DocCommentHighlightRules.getEndRule = function (start) {
    return {
        token : "comment.doc", // closing comment
        regex : "\\*\\/",
        next  : start
    };
};


exports.DocCommentHighlightRules = DocCommentHighlightRules;

});

define("ace/mode/javascript_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/doc_comment_highlight_rules","ace/mode/text_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var DocCommentHighlightRules = require("./doc_comment_highlight_rules").DocCommentHighlightRules;
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var identifierRe = "[a-zA-Z\\$_\u00a1-\uffff][a-zA-Z\\d\\$_\u00a1-\uffff]*";

var JavaScriptHighlightRules = function(options) {
    var keywordMapper = this.createKeywordMapper({
        "variable.language":
            "Array|Boolean|Date|Function|Iterator|Number|Object|RegExp|String|Proxy|"  + // Constructors
            "Namespace|QName|XML|XMLList|"                                             + // E4X
            "ArrayBuffer|Float32Array|Float64Array|Int16Array|Int32Array|Int8Array|"   +
            "Uint16Array|Uint32Array|Uint8Array|Uint8ClampedArray|"                    +
            "Error|EvalError|InternalError|RangeError|ReferenceError|StopIteration|"   + // Errors
            "SyntaxError|TypeError|URIError|"                                          +
            "decodeURI|decodeURIComponent|encodeURI|encodeURIComponent|eval|isFinite|" + // Non-constructor functions
            "isNaN|parseFloat|parseInt|"                                               +
            "JSON|Math|"                                                               + // Other
            "this|arguments|prototype|window|document"                                 , // Pseudo
        "keyword":
            "const|yield|import|get|set|async|await|" +
            "break|case|catch|continue|default|delete|do|else|finally|for|function|" +
            "if|in|of|instanceof|new|return|switch|throw|try|typeof|let|var|while|with|debugger|" +
            "__parent__|__count__|escape|unescape|with|__proto__|" +
            "class|enum|extends|super|export|implements|private|public|interface|package|protected|static",
        "storage.type":
            "const|let|var|function",
        "constant.language":
            "null|Infinity|NaN|undefined",
        "support.function":
            "alert",
        "constant.language.boolean": "true|false"
    }, "identifier");
    var kwBeforeRe = "case|do|else|finally|in|instanceof|return|throw|try|typeof|yield|void";

    var escapedRe = "\\\\(?:x[0-9a-fA-F]{2}|" + // hex
        "u[0-9a-fA-F]{4}|" + // unicode
        "u{[0-9a-fA-F]{1,6}}|" + // es6 unicode
        "[0-2][0-7]{0,2}|" + // oct
        "3[0-7][0-7]?|" + // oct
        "[4-7][0-7]?|" + //oct
        ".)";

    this.$rules = {
        "no_regex" : [
            DocCommentHighlightRules.getStartRule("doc-start"),
            comments("no_regex"),
            {
                token : "string",
                regex : "'(?=.)",
                next  : "qstring"
            }, {
                token : "string",
                regex : '"(?=.)',
                next  : "qqstring"
            }, {
                token : "constant.numeric", // hexadecimal, octal and binary
                regex : /0(?:[xX][0-9a-fA-F]+|[oO][0-7]+|[bB][01]+)\b/
            }, {
                token : "constant.numeric", // decimal integers and floats
                regex : /(?:\d\d*(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+\b)?/
            }, {
                token : [
                    "storage.type", "punctuation.operator", "support.function",
                    "punctuation.operator", "entity.name.function", "text","keyword.operator"
                ],
                regex : "(" + identifierRe + ")(\\.)(prototype)(\\.)(" + identifierRe +")(\\s*)(=)",
                next: "function_arguments"
            }, {
                token : [
                    "storage.type", "punctuation.operator", "entity.name.function", "text",
                    "keyword.operator", "text", "storage.type", "text", "paren.lparen"
                ],
                regex : "(" + identifierRe + ")(\\.)(" + identifierRe +")(\\s*)(=)(\\s*)(function)(\\s*)(\\()",
                next: "function_arguments"
            }, {
                token : [
                    "entity.name.function", "text", "keyword.operator", "text", "storage.type",
                    "text", "paren.lparen"
                ],
                regex : "(" + identifierRe +")(\\s*)(=)(\\s*)(function)(\\s*)(\\()",
                next: "function_arguments"
            }, {
                token : [
                    "storage.type", "punctuation.operator", "entity.name.function", "text",
                    "keyword.operator", "text",
                    "storage.type", "text", "entity.name.function", "text", "paren.lparen"
                ],
                regex : "(" + identifierRe + ")(\\.)(" + identifierRe +")(\\s*)(=)(\\s*)(function)(\\s+)(\\w+)(\\s*)(\\()",
                next: "function_arguments"
            }, {
                token : [
                    "storage.type", "text", "entity.name.function", "text", "paren.lparen"
                ],
                regex : "(function)(\\s+)(" + identifierRe + ")(\\s*)(\\()",
                next: "function_arguments"
            }, {
                token : [
                    "entity.name.function", "text", "punctuation.operator",
                    "text", "storage.type", "text", "paren.lparen"
                ],
                regex : "(" + identifierRe + ")(\\s*)(:)(\\s*)(function)(\\s*)(\\()",
                next: "function_arguments"
            }, {
                token : [
                    "text", "text", "storage.type", "text", "paren.lparen"
                ],
                regex : "(:)(\\s*)(function)(\\s*)(\\()",
                next: "function_arguments"
            }, {
                token : "keyword",
                regex : "from(?=\\s*('|\"))"
            }, {
                token : "keyword",
                regex : "(?:" + kwBeforeRe + ")\\b",
                next : "start"
            }, {
                token : ["support.constant"],
                regex : /that\b/
            }, {
                token : ["storage.type", "punctuation.operator", "support.function.firebug"],
                regex : /(console)(\.)(warn|info|log|error|time|trace|timeEnd|assert)\b/
            }, {
                token : keywordMapper,
                regex : identifierRe
            }, {
                token : "punctuation.operator",
                regex : /[.](?![.])/,
                next  : "property"
            }, {
                token : "storage.type",
                regex : /=>/,
                next  : "start"
            }, {
                token : "keyword.operator",
                regex : /--|\+\+|\.{3}|===|==|=|!=|!==|<+=?|>+=?|!|&&|\|\||\?:|[!$%&*+\-~\/^]=?/,
                next  : "start"
            }, {
                token : "punctuation.operator",
                regex : /[?:,;.]/,
                next  : "start"
            }, {
                token : "paren.lparen",
                regex : /[\[({]/,
                next  : "start"
            }, {
                token : "paren.rparen",
                regex : /[\])}]/
            }, {
                token: "comment",
                regex: /^#!.*$/
            }
        ],
        property: [{
                token : "text",
                regex : "\\s+"
            }, {
                token : [
                    "storage.type", "punctuation.operator", "entity.name.function", "text",
                    "keyword.operator", "text",
                    "storage.type", "text", "entity.name.function", "text", "paren.lparen"
                ],
                regex : "(" + identifierRe + ")(\\.)(" + identifierRe +")(\\s*)(=)(\\s*)(function)(?:(\\s+)(\\w+))?(\\s*)(\\()",
                next: "function_arguments"
            }, {
                token : "punctuation.operator",
                regex : /[.](?![.])/
            }, {
                token : "support.function",
                regex : /(s(?:h(?:ift|ow(?:Mod(?:elessDialog|alDialog)|Help))|croll(?:X|By(?:Pages|Lines)?|Y|To)?|t(?:op|rike)|i(?:n|zeToContent|debar|gnText)|ort|u(?:p|b(?:str(?:ing)?)?)|pli(?:ce|t)|e(?:nd|t(?:Re(?:sizable|questHeader)|M(?:i(?:nutes|lliseconds)|onth)|Seconds|Ho(?:tKeys|urs)|Year|Cursor|Time(?:out)?|Interval|ZOptions|Date|UTC(?:M(?:i(?:nutes|lliseconds)|onth)|Seconds|Hours|Date|FullYear)|FullYear|Active)|arch)|qrt|lice|avePreferences|mall)|h(?:ome|andleEvent)|navigate|c(?:har(?:CodeAt|At)|o(?:s|n(?:cat|textual|firm)|mpile)|eil|lear(?:Timeout|Interval)?|a(?:ptureEvents|ll)|reate(?:StyleSheet|Popup|EventObject))|t(?:o(?:GMTString|S(?:tring|ource)|U(?:TCString|pperCase)|Lo(?:caleString|werCase))|est|a(?:n|int(?:Enabled)?))|i(?:s(?:NaN|Finite)|ndexOf|talics)|d(?:isableExternalCapture|ump|etachEvent)|u(?:n(?:shift|taint|escape|watch)|pdateCommands)|j(?:oin|avaEnabled)|p(?:o(?:p|w)|ush|lugins.refresh|a(?:ddings|rse(?:Int|Float)?)|r(?:int|ompt|eference))|e(?:scape|nableExternalCapture|val|lementFromPoint|x(?:p|ec(?:Script|Command)?))|valueOf|UTC|queryCommand(?:State|Indeterm|Enabled|Value)|f(?:i(?:nd|le(?:ModifiedDate|Size|CreatedDate|UpdatedDate)|xed)|o(?:nt(?:size|color)|rward)|loor|romCharCode)|watch|l(?:ink|o(?:ad|g)|astIndexOf)|a(?:sin|nchor|cos|t(?:tachEvent|ob|an(?:2)?)|pply|lert|b(?:s|ort))|r(?:ou(?:nd|teEvents)|e(?:size(?:By|To)|calc|turnValue|place|verse|l(?:oad|ease(?:Capture|Events)))|andom)|g(?:o|et(?:ResponseHeader|M(?:i(?:nutes|lliseconds)|onth)|Se(?:conds|lection)|Hours|Year|Time(?:zoneOffset)?|Da(?:y|te)|UTC(?:M(?:i(?:nutes|lliseconds)|onth)|Seconds|Hours|Da(?:y|te)|FullYear)|FullYear|A(?:ttention|llResponseHeaders)))|m(?:in|ove(?:B(?:y|elow)|To(?:Absolute)?|Above)|ergeAttributes|a(?:tch|rgins|x))|b(?:toa|ig|o(?:ld|rderWidths)|link|ack))\b(?=\()/
            }, {
                token : "support.function.dom",
                regex : /(s(?:ub(?:stringData|mit)|plitText|e(?:t(?:NamedItem|Attribute(?:Node)?)|lect))|has(?:ChildNodes|Feature)|namedItem|c(?:l(?:ick|o(?:se|neNode))|reate(?:C(?:omment|DATASection|aption)|T(?:Head|extNode|Foot)|DocumentFragment|ProcessingInstruction|E(?:ntityReference|lement)|Attribute))|tabIndex|i(?:nsert(?:Row|Before|Cell|Data)|tem)|open|delete(?:Row|C(?:ell|aption)|T(?:Head|Foot)|Data)|focus|write(?:ln)?|a(?:dd|ppend(?:Child|Data))|re(?:set|place(?:Child|Data)|move(?:NamedItem|Child|Attribute(?:Node)?)?)|get(?:NamedItem|Element(?:sBy(?:Name|TagName|ClassName)|ById)|Attribute(?:Node)?)|blur)\b(?=\()/
            }, {
                token :  "support.constant",
                regex : /(s(?:ystemLanguage|cr(?:ipts|ollbars|een(?:X|Y|Top|Left))|t(?:yle(?:Sheets)?|atus(?:Text|bar)?)|ibling(?:Below|Above)|ource|uffixes|e(?:curity(?:Policy)?|l(?:ection|f)))|h(?:istory|ost(?:name)?|as(?:h|Focus))|y|X(?:MLDocument|SLDocument)|n(?:ext|ame(?:space(?:s|URI)|Prop))|M(?:IN_VALUE|AX_VALUE)|c(?:haracterSet|o(?:n(?:structor|trollers)|okieEnabled|lorDepth|mp(?:onents|lete))|urrent|puClass|l(?:i(?:p(?:boardData)?|entInformation)|osed|asses)|alle(?:e|r)|rypto)|t(?:o(?:olbar|p)|ext(?:Transform|Indent|Decoration|Align)|ags)|SQRT(?:1_2|2)|i(?:n(?:ner(?:Height|Width)|put)|ds|gnoreCase)|zIndex|o(?:scpu|n(?:readystatechange|Line)|uter(?:Height|Width)|p(?:sProfile|ener)|ffscreenBuffering)|NEGATIVE_INFINITY|d(?:i(?:splay|alog(?:Height|Top|Width|Left|Arguments)|rectories)|e(?:scription|fault(?:Status|Ch(?:ecked|arset)|View)))|u(?:ser(?:Profile|Language|Agent)|n(?:iqueID|defined)|pdateInterval)|_content|p(?:ixelDepth|ort|ersonalbar|kcs11|l(?:ugins|atform)|a(?:thname|dding(?:Right|Bottom|Top|Left)|rent(?:Window|Layer)?|ge(?:X(?:Offset)?|Y(?:Offset)?))|r(?:o(?:to(?:col|type)|duct(?:Sub)?|mpter)|e(?:vious|fix)))|e(?:n(?:coding|abledPlugin)|x(?:ternal|pando)|mbeds)|v(?:isibility|endor(?:Sub)?|Linkcolor)|URLUnencoded|P(?:I|OSITIVE_INFINITY)|f(?:ilename|o(?:nt(?:Size|Family|Weight)|rmName)|rame(?:s|Element)|gColor)|E|whiteSpace|l(?:i(?:stStyleType|n(?:eHeight|kColor))|o(?:ca(?:tion(?:bar)?|lName)|wsrc)|e(?:ngth|ft(?:Context)?)|a(?:st(?:M(?:odified|atch)|Index|Paren)|yer(?:s|X)|nguage))|a(?:pp(?:MinorVersion|Name|Co(?:deName|re)|Version)|vail(?:Height|Top|Width|Left)|ll|r(?:ity|guments)|Linkcolor|bove)|r(?:ight(?:Context)?|e(?:sponse(?:XML|Text)|adyState))|global|x|m(?:imeTypes|ultiline|enubar|argin(?:Right|Bottom|Top|Left))|L(?:N(?:10|2)|OG(?:10E|2E))|b(?:o(?:ttom|rder(?:Width|RightWidth|BottomWidth|Style|Color|TopWidth|LeftWidth))|ufferDepth|elow|ackground(?:Color|Image)))\b/
            }, {
                token : "identifier",
                regex : identifierRe
            }, {
                regex: "",
                token: "empty",
                next: "no_regex"
            }
        ],
        "start": [
            DocCommentHighlightRules.getStartRule("doc-start"),
            comments("start"),
            {
                token: "string.regexp",
                regex: "\\/",
                next: "regex"
            }, {
                token : "text",
                regex : "\\s+|^$",
                next : "start"
            }, {
                token: "empty",
                regex: "",
                next: "no_regex"
            }
        ],
        "regex": [
            {
                token: "regexp.keyword.operator",
                regex: "\\\\(?:u[\\da-fA-F]{4}|x[\\da-fA-F]{2}|.)"
            }, {
                token: "string.regexp",
                regex: "/[sxngimy]*",
                next: "no_regex"
            }, {
                token : "invalid",
                regex: /\{\d+\b,?\d*\}[+*]|[+*$^?][+*]|[$^][?]|\?{3,}/
            }, {
                token : "constant.language.escape",
                regex: /\(\?[:=!]|\)|\{\d+\b,?\d*\}|[+*]\?|[()$^+*?.]/
            }, {
                token : "constant.language.delimiter",
                regex: /\|/
            }, {
                token: "constant.language.escape",
                regex: /\[\^?/,
                next: "regex_character_class"
            }, {
                token: "empty",
                regex: "$",
                next: "no_regex"
            }, {
                defaultToken: "string.regexp"
            }
        ],
        "regex_character_class": [
            {
                token: "regexp.charclass.keyword.operator",
                regex: "\\\\(?:u[\\da-fA-F]{4}|x[\\da-fA-F]{2}|.)"
            }, {
                token: "constant.language.escape",
                regex: "]",
                next: "regex"
            }, {
                token: "constant.language.escape",
                regex: "-"
            }, {
                token: "empty",
                regex: "$",
                next: "no_regex"
            }, {
                defaultToken: "string.regexp.charachterclass"
            }
        ],
        "function_arguments": [
            {
                token: "variable.parameter",
                regex: identifierRe
            }, {
                token: "punctuation.operator",
                regex: "[, ]+"
            }, {
                token: "punctuation.operator",
                regex: "$"
            }, {
                token: "empty",
                regex: "",
                next: "no_regex"
            }
        ],
        "qqstring" : [
            {
                token : "constant.language.escape",
                regex : escapedRe
            }, {
                token : "string",
                regex : "\\\\$",
                consumeLineEnd  : true
            }, {
                token : "string",
                regex : '"|$',
                next  : "no_regex"
            }, {
                defaultToken: "string"
            }
        ],
        "qstring" : [
            {
                token : "constant.language.escape",
                regex : escapedRe
            }, {
                token : "string",
                regex : "\\\\$",
                consumeLineEnd  : true
            }, {
                token : "string",
                regex : "'|$",
                next  : "no_regex"
            }, {
                defaultToken: "string"
            }
        ]
    };


    if (!options || !options.noES6) {
        this.$rules.no_regex.unshift({
            regex: "[{}]", onMatch: function(val, state, stack) {
                this.next = val == "{" ? this.nextState : "";
                if (val == "{" && stack.length) {
                    stack.unshift("start", state);
                }
                else if (val == "}" && stack.length) {
                    stack.shift();
                    this.next = stack.shift();
                    if (this.next.indexOf("string") != -1 || this.next.indexOf("jsx") != -1)
                        return "paren.quasi.end";
                }
                return val == "{" ? "paren.lparen" : "paren.rparen";
            },
            nextState: "start"
        }, {
            token : "string.quasi.start",
            regex : /`/,
            push  : [{
                token : "constant.language.escape",
                regex : escapedRe
            }, {
                token : "paren.quasi.start",
                regex : /\${/,
                push  : "start"
            }, {
                token : "string.quasi.end",
                regex : /`/,
                next  : "pop"
            }, {
                defaultToken: "string.quasi"
            }]
        });

        if (!options || options.jsx != false)
            JSX.call(this);
    }

    this.embedRules(DocCommentHighlightRules, "doc-",
        [ DocCommentHighlightRules.getEndRule("no_regex") ]);

    this.normalizeRules();
};

oop.inherits(JavaScriptHighlightRules, TextHighlightRules);

function JSX() {
    var tagRegex = identifierRe.replace("\\d", "\\d\\-");
    var jsxTag = {
        onMatch : function(val, state, stack) {
            var offset = val.charAt(1) == "/" ? 2 : 1;
            if (offset == 1) {
                if (state != this.nextState)
                    stack.unshift(this.next, this.nextState, 0);
                else
                    stack.unshift(this.next);
                stack[2]++;
            } else if (offset == 2) {
                if (state == this.nextState) {
                    stack[1]--;
                    if (!stack[1] || stack[1] < 0) {
                        stack.shift();
                        stack.shift();
                    }
                }
            }
            return [{
                type: "meta.tag.punctuation." + (offset == 1 ? "" : "end-") + "tag-open.xml",
                value: val.slice(0, offset)
            }, {
                type: "meta.tag.tag-name.xml",
                value: val.substr(offset)
            }];
        },
        regex : "</?" + tagRegex + "",
        next: "jsxAttributes",
        nextState: "jsx"
    };
    this.$rules.start.unshift(jsxTag);
    var jsxJsRule = {
        regex: "{",
        token: "paren.quasi.start",
        push: "start"
    };
    this.$rules.jsx = [
        jsxJsRule,
        jsxTag,
        {include : "reference"},
        {defaultToken: "string"}
    ];
    this.$rules.jsxAttributes = [{
        token : "meta.tag.punctuation.tag-close.xml",
        regex : "/?>",
        onMatch : function(value, currentState, stack) {
            if (currentState == stack[0])
                stack.shift();
            if (value.length == 2) {
                if (stack[0] == this.nextState)
                    stack[1]--;
                if (!stack[1] || stack[1] < 0) {
                    stack.splice(0, 2);
                }
            }
            this.next = stack[0] || "start";
            return [{type: this.token, value: value}];
        },
        nextState: "jsx"
    },
    jsxJsRule,
    comments("jsxAttributes"),
    {
        token : "entity.other.attribute-name.xml",
        regex : tagRegex
    }, {
        token : "keyword.operator.attribute-equals.xml",
        regex : "="
    }, {
        token : "text.tag-whitespace.xml",
        regex : "\\s+"
    }, {
        token : "string.attribute-value.xml",
        regex : "'",
        stateName : "jsx_attr_q",
        push : [
            {token : "string.attribute-value.xml", regex: "'", next: "pop"},
            {include : "reference"},
            {defaultToken : "string.attribute-value.xml"}
        ]
    }, {
        token : "string.attribute-value.xml",
        regex : '"',
        stateName : "jsx_attr_qq",
        push : [
            {token : "string.attribute-value.xml", regex: '"', next: "pop"},
            {include : "reference"},
            {defaultToken : "string.attribute-value.xml"}
        ]
    },
    jsxTag
    ];
    this.$rules.reference = [{
        token : "constant.language.escape.reference.xml",
        regex : "(?:&#[0-9]+;)|(?:&#x[0-9a-fA-F]+;)|(?:&[a-zA-Z0-9_:\\.-]+;)"
    }];
}

function comments(next) {
    return [
        {
            token : "comment", // multi line comment
            regex : /\/\*/,
            next: [
                DocCommentHighlightRules.getTagRule(),
                {token : "comment", regex : "\\*\\/", next : next || "pop"},
                {defaultToken : "comment", caseInsensitive: true}
            ]
        }, {
            token : "comment",
            regex : "\\/\\/",
            next: [
                DocCommentHighlightRules.getTagRule(),
                {token : "comment", regex : "$|^", next : next || "pop"},
                {defaultToken : "comment", caseInsensitive: true}
            ]
        }
    ];
}
exports.JavaScriptHighlightRules = JavaScriptHighlightRules;
});

define("ace/mode/matching_brace_outdent",["require","exports","module","ace/range"], function(require, exports, module) {
"use strict";

var Range = require("../range").Range;

var MatchingBraceOutdent = function() {};

(function() {

    this.checkOutdent = function(line, input) {
        if (! /^\s+$/.test(line))
            return false;

        return /^\s*\}/.test(input);
    };

    this.autoOutdent = function(doc, row) {
        var line = doc.getLine(row);
        var match = line.match(/^(\s*\})/);

        if (!match) return 0;

        var column = match[1].length;
        var openBracePos = doc.findMatchingBracket({row: row, column: column});

        if (!openBracePos || openBracePos.row == row) return 0;

        var indent = this.$getIndent(doc.getLine(openBracePos.row));
        doc.replace(new Range(row, 0, row, column-1), indent);
    };

    this.$getIndent = function(line) {
        return line.match(/^\s*/)[0];
    };

}).call(MatchingBraceOutdent.prototype);

exports.MatchingBraceOutdent = MatchingBraceOutdent;
});

define("ace/mode/folding/cstyle",["require","exports","module","ace/lib/oop","ace/range","ace/mode/folding/fold_mode"], function(require, exports, module) {
"use strict";

var oop = require("../../lib/oop");
var Range = require("../../range").Range;
var BaseFoldMode = require("./fold_mode").FoldMode;

var FoldMode = exports.FoldMode = function(commentRegex) {
    if (commentRegex) {
        this.foldingStartMarker = new RegExp(
            this.foldingStartMarker.source.replace(/\|[^|]*?$/, "|" + commentRegex.start)
        );
        this.foldingStopMarker = new RegExp(
            this.foldingStopMarker.source.replace(/\|[^|]*?$/, "|" + commentRegex.end)
        );
    }
};
oop.inherits(FoldMode, BaseFoldMode);

(function() {
    
    this.foldingStartMarker = /([\{\[\(])[^\}\]\)]*$|^\s*(\/\*)/;
    this.foldingStopMarker = /^[^\[\{\(]*([\}\]\)])|^[\s\*]*(\*\/)/;
    this.singleLineBlockCommentRe= /^\s*(\/\*).*\*\/\s*$/;
    this.tripleStarBlockCommentRe = /^\s*(\/\*\*\*).*\*\/\s*$/;
    this.startRegionRe = /^\s*(\/\*|\/\/)#?region\b/;
    this._getFoldWidgetBase = this.getFoldWidget;
    this.getFoldWidget = function(session, foldStyle, row) {
        var line = session.getLine(row);
    
        if (this.singleLineBlockCommentRe.test(line)) {
            if (!this.startRegionRe.test(line) && !this.tripleStarBlockCommentRe.test(line))
                return "";
        }
    
        var fw = this._getFoldWidgetBase(session, foldStyle, row);
    
        if (!fw && this.startRegionRe.test(line))
            return "start"; // lineCommentRegionStart
    
        return fw;
    };

    this.getFoldWidgetRange = function(session, foldStyle, row, forceMultiline) {
        var line = session.getLine(row);
        
        if (this.startRegionRe.test(line))
            return this.getCommentRegionBlock(session, line, row);
        
        var match = line.match(this.foldingStartMarker);
        if (match) {
            var i = match.index;

            if (match[1])
                return this.openingBracketBlock(session, match[1], row, i);
                
            var range = session.getCommentFoldRange(row, i + match[0].length, 1);
            
            if (range && !range.isMultiLine()) {
                if (forceMultiline) {
                    range = this.getSectionRange(session, row);
                } else if (foldStyle != "all")
                    range = null;
            }
            
            return range;
        }

        if (foldStyle === "markbegin")
            return;

        var match = line.match(this.foldingStopMarker);
        if (match) {
            var i = match.index + match[0].length;

            if (match[1])
                return this.closingBracketBlock(session, match[1], row, i);

            return session.getCommentFoldRange(row, i, -1);
        }
    };
    
    this.getSectionRange = function(session, row) {
        var line = session.getLine(row);
        var startIndent = line.search(/\S/);
        var startRow = row;
        var startColumn = line.length;
        row = row + 1;
        var endRow = row;
        var maxRow = session.getLength();
        while (++row < maxRow) {
            line = session.getLine(row);
            var indent = line.search(/\S/);
            if (indent === -1)
                continue;
            if  (startIndent > indent)
                break;
            var subRange = this.getFoldWidgetRange(session, "all", row);
            
            if (subRange) {
                if (subRange.start.row <= startRow) {
                    break;
                } else if (subRange.isMultiLine()) {
                    row = subRange.end.row;
                } else if (startIndent == indent) {
                    break;
                }
            }
            endRow = row;
        }
        
        return new Range(startRow, startColumn, endRow, session.getLine(endRow).length);
    };
    this.getCommentRegionBlock = function(session, line, row) {
        var startColumn = line.search(/\s*$/);
        var maxRow = session.getLength();
        var startRow = row;
        
        var re = /^\s*(?:\/\*|\/\/|--)#?(end)?region\b/;
        var depth = 1;
        while (++row < maxRow) {
            line = session.getLine(row);
            var m = re.exec(line);
            if (!m) continue;
            if (m[1]) depth--;
            else depth++;

            if (!depth) break;
        }

        var endRow = row;
        if (endRow > startRow) {
            return new Range(startRow, startColumn, endRow, line.length);
        }
    };

}).call(FoldMode.prototype);

});

define("ace/mode/javascript",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/javascript_highlight_rules","ace/mode/matching_brace_outdent","ace/worker/worker_client","ace/mode/behaviour/cstyle","ace/mode/folding/cstyle"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var JavaScriptHighlightRules = require("./javascript_highlight_rules").JavaScriptHighlightRules;
var MatchingBraceOutdent = require("./matching_brace_outdent").MatchingBraceOutdent;
var WorkerClient = require("../worker/worker_client").WorkerClient;
var CstyleBehaviour = require("./behaviour/cstyle").CstyleBehaviour;
var CStyleFoldMode = require("./folding/cstyle").FoldMode;

var Mode = function() {
    this.HighlightRules = JavaScriptHighlightRules;
    
    this.$outdent = new MatchingBraceOutdent();
    this.$behaviour = new CstyleBehaviour();
    this.foldingRules = new CStyleFoldMode();
};
oop.inherits(Mode, TextMode);

(function() {

    this.lineCommentStart = "//";
    this.blockComment = {start: "/*", end: "*/"};
    this.$quotes = {'"': '"', "'": "'", "`": "`"};

    this.getNextLineIndent = function(state, line, tab) {
        var indent = this.$getIndent(line);

        var tokenizedLine = this.getTokenizer().getLineTokens(line, state);
        var tokens = tokenizedLine.tokens;
        var endState = tokenizedLine.state;

        if (tokens.length && tokens[tokens.length-1].type == "comment") {
            return indent;
        }

        if (state == "start" || state == "no_regex") {
            var match = line.match(/^.*(?:\bcase\b.*:|[\{\(\[])\s*$/);
            if (match) {
                indent += tab;
            }
        } else if (state == "doc-start") {
            if (endState == "start" || endState == "no_regex") {
                return "";
            }
            var match = line.match(/^\s*(\/?)\*/);
            if (match) {
                if (match[1]) {
                    indent += " ";
                }
                indent += "* ";
            }
        }

        return indent;
    };

    this.checkOutdent = function(state, line, input) {
        return this.$outdent.checkOutdent(line, input);
    };

    this.autoOutdent = function(state, doc, row) {
        this.$outdent.autoOutdent(doc, row);
    };

    this.createWorker = function(session) {
        var worker = new WorkerClient(["ace"], "ace/mode/javascript_worker", "JavaScriptWorker");
        worker.attachToDocument(session.getDocument());

        worker.on("annotate", function(results) {
            session.setAnnotations(results.data);
        });

        worker.on("terminate", function() {
            session.clearAnnotations();
        });

        return worker;
    };

    this.$id = "ace/mode/javascript";
}).call(Mode.prototype);

exports.Mode = Mode;
});

define("ace/mode/xml_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;

var XmlHighlightRules = function(normalize) {
    var tagRegex = "[_:a-zA-Z\xc0-\uffff][-_:.a-zA-Z0-9\xc0-\uffff]*";

    this.$rules = {
        start : [
            {token : "string.cdata.xml", regex : "<\\!\\[CDATA\\[", next : "cdata"},
            {
                token : ["punctuation.instruction.xml", "keyword.instruction.xml"],
                regex : "(<\\?)(" + tagRegex + ")", next : "processing_instruction"
            },
            {token : "comment.start.xml", regex : "<\\!--", next : "comment"},
            {
                token : ["xml-pe.doctype.xml", "xml-pe.doctype.xml"],
                regex : "(<\\!)(DOCTYPE)(?=[\\s])", next : "doctype", caseInsensitive: true
            },
            {include : "tag"},
            {token : "text.end-tag-open.xml", regex: "</"},
            {token : "text.tag-open.xml", regex: "<"},
            {include : "reference"},
            {defaultToken : "text.xml"}
        ],

        processing_instruction : [{
            token : "entity.other.attribute-name.decl-attribute-name.xml",
            regex : tagRegex
        }, {
            token : "keyword.operator.decl-attribute-equals.xml",
            regex : "="
        }, {
            include: "whitespace"
        }, {
            include: "string"
        }, {
            token : "punctuation.xml-decl.xml",
            regex : "\\?>",
            next : "start"
        }],

        doctype : [
            {include : "whitespace"},
            {include : "string"},
            {token : "xml-pe.doctype.xml", regex : ">", next : "start"},
            {token : "xml-pe.xml", regex : "[-_a-zA-Z0-9:]+"},
            {token : "punctuation.int-subset", regex : "\\[", push : "int_subset"}
        ],

        int_subset : [{
            token : "text.xml",
            regex : "\\s+"
        }, {
            token: "punctuation.int-subset.xml",
            regex: "]",
            next: "pop"
        }, {
            token : ["punctuation.markup-decl.xml", "keyword.markup-decl.xml"],
            regex : "(<\\!)(" + tagRegex + ")",
            push : [{
                token : "text",
                regex : "\\s+"
            },
            {
                token : "punctuation.markup-decl.xml",
                regex : ">",
                next : "pop"
            },
            {include : "string"}]
        }],

        cdata : [
            {token : "string.cdata.xml", regex : "\\]\\]>", next : "start"},
            {token : "text.xml", regex : "\\s+"},
            {token : "text.xml", regex : "(?:[^\\]]|\\](?!\\]>))+"}
        ],

        comment : [
            {token : "comment.end.xml", regex : "-->", next : "start"},
            {defaultToken : "comment.xml"}
        ],

        reference : [{
            token : "constant.language.escape.reference.xml",
            regex : "(?:&#[0-9]+;)|(?:&#x[0-9a-fA-F]+;)|(?:&[a-zA-Z0-9_:\\.-]+;)"
        }],

        attr_reference : [{
            token : "constant.language.escape.reference.attribute-value.xml",
            regex : "(?:&#[0-9]+;)|(?:&#x[0-9a-fA-F]+;)|(?:&[a-zA-Z0-9_:\\.-]+;)"
        }],

        tag : [{
            token : ["meta.tag.punctuation.tag-open.xml", "meta.tag.punctuation.end-tag-open.xml", "meta.tag.tag-name.xml"],
            regex : "(?:(<)|(</))((?:" + tagRegex + ":)?" + tagRegex + ")",
            next: [
                {include : "attributes"},
                {token : "meta.tag.punctuation.tag-close.xml", regex : "/?>", next : "start"}
            ]
        }],

        tag_whitespace : [
            {token : "text.tag-whitespace.xml", regex : "\\s+"}
        ],
        whitespace : [
            {token : "text.whitespace.xml", regex : "\\s+"}
        ],
        string: [{
            token : "string.xml",
            regex : "'",
            push : [
                {token : "string.xml", regex: "'", next: "pop"},
                {defaultToken : "string.xml"}
            ]
        }, {
            token : "string.xml",
            regex : '"',
            push : [
                {token : "string.xml", regex: '"', next: "pop"},
                {defaultToken : "string.xml"}
            ]
        }],

        attributes: [{
            token : "entity.other.attribute-name.xml",
            regex : tagRegex
        }, {
            token : "keyword.operator.attribute-equals.xml",
            regex : "="
        }, {
            include: "tag_whitespace"
        }, {
            include: "attribute_value"
        }],

        attribute_value: [{
            token : "string.attribute-value.xml",
            regex : "'",
            push : [
                {token : "string.attribute-value.xml", regex: "'", next: "pop"},
                {include : "attr_reference"},
                {defaultToken : "string.attribute-value.xml"}
            ]
        }, {
            token : "string.attribute-value.xml",
            regex : '"',
            push : [
                {token : "string.attribute-value.xml", regex: '"', next: "pop"},
                {include : "attr_reference"},
                {defaultToken : "string.attribute-value.xml"}
            ]
        }]
    };

    if (this.constructor === XmlHighlightRules)
        this.normalizeRules();
};


(function() {

    this.embedTagRules = function(HighlightRules, prefix, tag){
        this.$rules.tag.unshift({
            token : ["meta.tag.punctuation.tag-open.xml", "meta.tag." + tag + ".tag-name.xml"],
            regex : "(<)(" + tag + "(?=\\s|>|$))",
            next: [
                {include : "attributes"},
                {token : "meta.tag.punctuation.tag-close.xml", regex : "/?>", next : prefix + "start"}
            ]
        });

        this.$rules[tag + "-end"] = [
            {include : "attributes"},
            {token : "meta.tag.punctuation.tag-close.xml", regex : "/?>",  next: "start",
                onMatch : function(value, currentState, stack) {
                    stack.splice(0);
                    return this.token;
            }}
        ];

        this.embedRules(HighlightRules, prefix, [{
            token: ["meta.tag.punctuation.end-tag-open.xml", "meta.tag." + tag + ".tag-name.xml"],
            regex : "(</)(" + tag + "(?=\\s|>|$))",
            next: tag + "-end"
        }, {
            token: "string.cdata.xml",
            regex : "<\\!\\[CDATA\\["
        }, {
            token: "string.cdata.xml",
            regex : "\\]\\]>"
        }]);
    };

}).call(TextHighlightRules.prototype);

oop.inherits(XmlHighlightRules, TextHighlightRules);

exports.XmlHighlightRules = XmlHighlightRules;
});

define("ace/mode/behaviour/xml",["require","exports","module","ace/lib/oop","ace/mode/behaviour","ace/token_iterator","ace/lib/lang"], function(require, exports, module) {
"use strict";

var oop = require("../../lib/oop");
var Behaviour = require("../behaviour").Behaviour;
var TokenIterator = require("../../token_iterator").TokenIterator;
var lang = require("../../lib/lang");

function is(token, type) {
    return token && token.type.lastIndexOf(type + ".xml") > -1;
}

var XmlBehaviour = function () {

    this.add("string_dquotes", "insertion", function (state, action, editor, session, text) {
        if (text == '"' || text == "'") {
            var quote = text;
            var selected = session.doc.getTextRange(editor.getSelectionRange());
            if (selected !== "" && selected !== "'" && selected != '"' && editor.getWrapBehavioursEnabled()) {
                return {
                    text: quote + selected + quote,
                    selection: false
                };
            }

            var cursor = editor.getCursorPosition();
            var line = session.doc.getLine(cursor.row);
            var rightChar = line.substring(cursor.column, cursor.column + 1);
            var iterator = new TokenIterator(session, cursor.row, cursor.column);
            var token = iterator.getCurrentToken();

            if (rightChar == quote && (is(token, "attribute-value") || is(token, "string"))) {
                return {
                    text: "",
                    selection: [1, 1]
                };
            }

            if (!token)
                token = iterator.stepBackward();

            if (!token)
                return;

            while (is(token, "tag-whitespace") || is(token, "whitespace")) {
                token = iterator.stepBackward();
            }
            var rightSpace = !rightChar || rightChar.match(/\s/);
            if (is(token, "attribute-equals") && (rightSpace || rightChar == '>') || (is(token, "decl-attribute-equals") && (rightSpace || rightChar == '?'))) {
                return {
                    text: quote + quote,
                    selection: [1, 1]
                };
            }
        }
    });

    this.add("string_dquotes", "deletion", function(state, action, editor, session, range) {
        var selected = session.doc.getTextRange(range);
        if (!range.isMultiLine() && (selected == '"' || selected == "'")) {
            var line = session.doc.getLine(range.start.row);
            var rightChar = line.substring(range.start.column + 1, range.start.column + 2);
            if (rightChar == selected) {
                range.end.column++;
                return range;
            }
        }
    });

    this.add("autoclosing", "insertion", function (state, action, editor, session, text) {
        if (text == '>') {
            var position = editor.getSelectionRange().start;
            var iterator = new TokenIterator(session, position.row, position.column);
            var token = iterator.getCurrentToken() || iterator.stepBackward();
            if (!token || !(is(token, "tag-name") || is(token, "tag-whitespace") || is(token, "attribute-name") || is(token, "attribute-equals") || is(token, "attribute-value")))
                return;
            if (is(token, "reference.attribute-value"))
                return;
            if (is(token, "attribute-value")) {
                var tokenEndColumn = iterator.getCurrentTokenColumn() + token.value.length;
                if (position.column < tokenEndColumn)
                    return;
                if (position.column == tokenEndColumn) {
                    var nextToken = iterator.stepForward();
                    if (nextToken && is(nextToken, "attribute-value"))
                        return;
                    iterator.stepBackward();
                }
            }
            
            if (/^\s*>/.test(session.getLine(position.row).slice(position.column)))
                return;
            while (!is(token, "tag-name")) {
                token = iterator.stepBackward();
                if (token.value == "<") {
                    token = iterator.stepForward();
                    break;
                }
            }

            var tokenRow = iterator.getCurrentTokenRow();
            var tokenColumn = iterator.getCurrentTokenColumn();
            if (is(iterator.stepBackward(), "end-tag-open"))
                return;

            var element = token.value;
            if (tokenRow == position.row)
                element = element.substring(0, position.column - tokenColumn);

            if (this.voidElements.hasOwnProperty(element.toLowerCase()))
                 return;

            return {
               text: ">" + "</" + element + ">",
               selection: [1, 1]
            };
        }
    });

    this.add("autoindent", "insertion", function (state, action, editor, session, text) {
        if (text == "\n") {
            var cursor = editor.getCursorPosition();
            var line = session.getLine(cursor.row);
            var iterator = new TokenIterator(session, cursor.row, cursor.column);
            var token = iterator.getCurrentToken();

            if (token && token.type.indexOf("tag-close") !== -1) {
                if (token.value == "/>")
                    return;
                while (token && token.type.indexOf("tag-name") === -1) {
                    token = iterator.stepBackward();
                }

                if (!token) {
                    return;
                }

                var tag = token.value;
                var row = iterator.getCurrentTokenRow();
                token = iterator.stepBackward();
                if (!token || token.type.indexOf("end-tag") !== -1) {
                    return;
                }

                if (this.voidElements && !this.voidElements[tag]) {
                    var nextToken = session.getTokenAt(cursor.row, cursor.column+1);
                    var line = session.getLine(row);
                    var nextIndent = this.$getIndent(line);
                    var indent = nextIndent + session.getTabString();

                    if (nextToken && nextToken.value === "</") {
                        return {
                            text: "\n" + indent + "\n" + nextIndent,
                            selection: [1, indent.length, 1, indent.length]
                        };
                    } else {
                        return {
                            text: "\n" + indent
                        };
                    }
                }
            }
        }
    });

};

oop.inherits(XmlBehaviour, Behaviour);

exports.XmlBehaviour = XmlBehaviour;
});

define("ace/mode/folding/xml",["require","exports","module","ace/lib/oop","ace/lib/lang","ace/range","ace/mode/folding/fold_mode","ace/token_iterator"], function(require, exports, module) {
"use strict";

var oop = require("../../lib/oop");
var lang = require("../../lib/lang");
var Range = require("../../range").Range;
var BaseFoldMode = require("./fold_mode").FoldMode;
var TokenIterator = require("../../token_iterator").TokenIterator;

var FoldMode = exports.FoldMode = function(voidElements, optionalEndTags) {
    BaseFoldMode.call(this);
    this.voidElements = voidElements || {};
    this.optionalEndTags = oop.mixin({}, this.voidElements);
    if (optionalEndTags)
        oop.mixin(this.optionalEndTags, optionalEndTags);
    
};
oop.inherits(FoldMode, BaseFoldMode);

var Tag = function() {
    this.tagName = "";
    this.closing = false;
    this.selfClosing = false;
    this.start = {row: 0, column: 0};
    this.end = {row: 0, column: 0};
};

function is(token, type) {
    return token.type.lastIndexOf(type + ".xml") > -1;
}

(function() {

    this.getFoldWidget = function(session, foldStyle, row) {
        var tag = this._getFirstTagInLine(session, row);

        if (!tag)
            return this.getCommentFoldWidget(session, row);

        if (tag.closing || (!tag.tagName && tag.selfClosing))
            return foldStyle == "markbeginend" ? "end" : "";

        if (!tag.tagName || tag.selfClosing || this.voidElements.hasOwnProperty(tag.tagName.toLowerCase()))
            return "";

        if (this._findEndTagInLine(session, row, tag.tagName, tag.end.column))
            return "";

        return "start";
    };
    
    this.getCommentFoldWidget = function(session, row) {
        if (/comment/.test(session.getState(row)) && /<!-/.test(session.getLine(row)))
            return "start";
        return "";
    };
    this._getFirstTagInLine = function(session, row) {
        var tokens = session.getTokens(row);
        var tag = new Tag();

        for (var i = 0; i < tokens.length; i++) {
            var token = tokens[i];
            if (is(token, "tag-open")) {
                tag.end.column = tag.start.column + token.value.length;
                tag.closing = is(token, "end-tag-open");
                token = tokens[++i];
                if (!token)
                    return null;
                tag.tagName = token.value;
                tag.end.column += token.value.length;
                for (i++; i < tokens.length; i++) {
                    token = tokens[i];
                    tag.end.column += token.value.length;
                    if (is(token, "tag-close")) {
                        tag.selfClosing = token.value == '/>';
                        break;
                    }
                }
                return tag;
            } else if (is(token, "tag-close")) {
                tag.selfClosing = token.value == '/>';
                return tag;
            }
            tag.start.column += token.value.length;
        }

        return null;
    };

    this._findEndTagInLine = function(session, row, tagName, startColumn) {
        var tokens = session.getTokens(row);
        var column = 0;
        for (var i = 0; i < tokens.length; i++) {
            var token = tokens[i];
            column += token.value.length;
            if (column < startColumn)
                continue;
            if (is(token, "end-tag-open")) {
                token = tokens[i + 1];
                if (token && token.value == tagName)
                    return true;
            }
        }
        return false;
    };
    this._readTagForward = function(iterator) {
        var token = iterator.getCurrentToken();
        if (!token)
            return null;

        var tag = new Tag();
        do {
            if (is(token, "tag-open")) {
                tag.closing = is(token, "end-tag-open");
                tag.start.row = iterator.getCurrentTokenRow();
                tag.start.column = iterator.getCurrentTokenColumn();
            } else if (is(token, "tag-name")) {
                tag.tagName = token.value;
            } else if (is(token, "tag-close")) {
                tag.selfClosing = token.value == "/>";
                tag.end.row = iterator.getCurrentTokenRow();
                tag.end.column = iterator.getCurrentTokenColumn() + token.value.length;
                iterator.stepForward();
                return tag;
            }
        } while(token = iterator.stepForward());

        return null;
    };
    
    this._readTagBackward = function(iterator) {
        var token = iterator.getCurrentToken();
        if (!token)
            return null;

        var tag = new Tag();
        do {
            if (is(token, "tag-open")) {
                tag.closing = is(token, "end-tag-open");
                tag.start.row = iterator.getCurrentTokenRow();
                tag.start.column = iterator.getCurrentTokenColumn();
                iterator.stepBackward();
                return tag;
            } else if (is(token, "tag-name")) {
                tag.tagName = token.value;
            } else if (is(token, "tag-close")) {
                tag.selfClosing = token.value == "/>";
                tag.end.row = iterator.getCurrentTokenRow();
                tag.end.column = iterator.getCurrentTokenColumn() + token.value.length;
            }
        } while(token = iterator.stepBackward());

        return null;
    };
    
    this._pop = function(stack, tag) {
        while (stack.length) {
            
            var top = stack[stack.length-1];
            if (!tag || top.tagName == tag.tagName) {
                return stack.pop();
            }
            else if (this.optionalEndTags.hasOwnProperty(top.tagName)) {
                stack.pop();
                continue;
            } else {
                return null;
            }
        }
    };
    
    this.getFoldWidgetRange = function(session, foldStyle, row) {
        var firstTag = this._getFirstTagInLine(session, row);
        
        if (!firstTag) {
            return this.getCommentFoldWidget(session, row)
                && session.getCommentFoldRange(row, session.getLine(row).length);
        }
        
        var isBackward = firstTag.closing || firstTag.selfClosing;
        var stack = [];
        var tag;
        
        if (!isBackward) {
            var iterator = new TokenIterator(session, row, firstTag.start.column);
            var start = {
                row: row,
                column: firstTag.start.column + firstTag.tagName.length + 2
            };
            if (firstTag.start.row == firstTag.end.row)
                start.column = firstTag.end.column;
            while (tag = this._readTagForward(iterator)) {
                if (tag.selfClosing) {
                    if (!stack.length) {
                        tag.start.column += tag.tagName.length + 2;
                        tag.end.column -= 2;
                        return Range.fromPoints(tag.start, tag.end);
                    } else
                        continue;
                }
                
                if (tag.closing) {
                    this._pop(stack, tag);
                    if (stack.length == 0)
                        return Range.fromPoints(start, tag.start);
                }
                else {
                    stack.push(tag);
                }
            }
        }
        else {
            var iterator = new TokenIterator(session, row, firstTag.end.column);
            var end = {
                row: row,
                column: firstTag.start.column
            };
            
            while (tag = this._readTagBackward(iterator)) {
                if (tag.selfClosing) {
                    if (!stack.length) {
                        tag.start.column += tag.tagName.length + 2;
                        tag.end.column -= 2;
                        return Range.fromPoints(tag.start, tag.end);
                    } else
                        continue;
                }
                
                if (!tag.closing) {
                    this._pop(stack, tag);
                    if (stack.length == 0) {
                        tag.start.column += tag.tagName.length + 2;
                        if (tag.start.row == tag.end.row && tag.start.column < tag.end.column)
                            tag.start.column = tag.end.column;
                        return Range.fromPoints(tag.start, end);
                    }
                }
                else {
                    stack.push(tag);
                }
            }
        }
        
    };

}).call(FoldMode.prototype);

});

define("ace/mode/xml",["require","exports","module","ace/lib/oop","ace/lib/lang","ace/mode/text","ace/mode/xml_highlight_rules","ace/mode/behaviour/xml","ace/mode/folding/xml","ace/worker/worker_client"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var lang = require("../lib/lang");
var TextMode = require("./text").Mode;
var XmlHighlightRules = require("./xml_highlight_rules").XmlHighlightRules;
var XmlBehaviour = require("./behaviour/xml").XmlBehaviour;
var XmlFoldMode = require("./folding/xml").FoldMode;
var WorkerClient = require("../worker/worker_client").WorkerClient;

var Mode = function() {
   this.HighlightRules = XmlHighlightRules;
   this.$behaviour = new XmlBehaviour();
   this.foldingRules = new XmlFoldMode();
};

oop.inherits(Mode, TextMode);

(function() {

    this.voidElements = lang.arrayToMap([]);

    this.blockComment = {start: "<!--", end: "-->"};

    this.createWorker = function(session) {
        var worker = new WorkerClient(["ace"], "ace/mode/xml_worker", "Worker");
        worker.attachToDocument(session.getDocument());

        worker.on("error", function(e) {
            session.setAnnotations(e.data);
        });

        worker.on("terminate", function() {
            session.clearAnnotations();
        });

        return worker;
    };
    
    this.$id = "ace/mode/xml";
}).call(Mode.prototype);

exports.Mode = Mode;
});

define("ace/mode/css_highlight_rules",["require","exports","module","ace/lib/oop","ace/lib/lang","ace/mode/text_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var lang = require("../lib/lang");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var supportType = exports.supportType = "align-content|align-items|align-self|all|animation|animation-delay|animation-direction|animation-duration|animation-fill-mode|animation-iteration-count|animation-name|animation-play-state|animation-timing-function|backface-visibility|background|background-attachment|background-blend-mode|background-clip|background-color|background-image|background-origin|background-position|background-repeat|background-size|border|border-bottom|border-bottom-color|border-bottom-left-radius|border-bottom-right-radius|border-bottom-style|border-bottom-width|border-collapse|border-color|border-image|border-image-outset|border-image-repeat|border-image-slice|border-image-source|border-image-width|border-left|border-left-color|border-left-style|border-left-width|border-radius|border-right|border-right-color|border-right-style|border-right-width|border-spacing|border-style|border-top|border-top-color|border-top-left-radius|border-top-right-radius|border-top-style|border-top-width|border-width|bottom|box-shadow|box-sizing|caption-side|clear|clip|color|column-count|column-fill|column-gap|column-rule|column-rule-color|column-rule-style|column-rule-width|column-span|column-width|columns|content|counter-increment|counter-reset|cursor|direction|display|empty-cells|filter|flex|flex-basis|flex-direction|flex-flow|flex-grow|flex-shrink|flex-wrap|float|font|font-family|font-size|font-size-adjust|font-stretch|font-style|font-variant|font-weight|hanging-punctuation|height|justify-content|left|letter-spacing|line-height|list-style|list-style-image|list-style-position|list-style-type|margin|margin-bottom|margin-left|margin-right|margin-top|max-height|max-width|max-zoom|min-height|min-width|min-zoom|nav-down|nav-index|nav-left|nav-right|nav-up|opacity|order|outline|outline-color|outline-offset|outline-style|outline-width|overflow|overflow-x|overflow-y|padding|padding-bottom|padding-left|padding-right|padding-top|page-break-after|page-break-before|page-break-inside|perspective|perspective-origin|position|quotes|resize|right|tab-size|table-layout|text-align|text-align-last|text-decoration|text-decoration-color|text-decoration-line|text-decoration-style|text-indent|text-justify|text-overflow|text-shadow|text-transform|top|transform|transform-origin|transform-style|transition|transition-delay|transition-duration|transition-property|transition-timing-function|unicode-bidi|user-select|user-zoom|vertical-align|visibility|white-space|width|word-break|word-spacing|word-wrap|z-index";
var supportFunction = exports.supportFunction = "rgb|rgba|url|attr|counter|counters";
var supportConstant = exports.supportConstant = "absolute|after-edge|after|all-scroll|all|alphabetic|always|antialiased|armenian|auto|avoid-column|avoid-page|avoid|balance|baseline|before-edge|before|below|bidi-override|block-line-height|block|bold|bolder|border-box|both|bottom|box|break-all|break-word|capitalize|caps-height|caption|center|central|char|circle|cjk-ideographic|clone|close-quote|col-resize|collapse|column|consider-shifts|contain|content-box|cover|crosshair|cubic-bezier|dashed|decimal-leading-zero|decimal|default|disabled|disc|disregard-shifts|distribute-all-lines|distribute-letter|distribute-space|distribute|dotted|double|e-resize|ease-in|ease-in-out|ease-out|ease|ellipsis|end|exclude-ruby|fill|fixed|georgian|glyphs|grid-height|groove|hand|hanging|hebrew|help|hidden|hiragana-iroha|hiragana|horizontal|icon|ideograph-alpha|ideograph-numeric|ideograph-parenthesis|ideograph-space|ideographic|inactive|include-ruby|inherit|initial|inline-block|inline-box|inline-line-height|inline-table|inline|inset|inside|inter-ideograph|inter-word|invert|italic|justify|katakana-iroha|katakana|keep-all|last|left|lighter|line-edge|line-through|line|linear|list-item|local|loose|lower-alpha|lower-greek|lower-latin|lower-roman|lowercase|lr-tb|ltr|mathematical|max-height|max-size|medium|menu|message-box|middle|move|n-resize|ne-resize|newspaper|no-change|no-close-quote|no-drop|no-open-quote|no-repeat|none|normal|not-allowed|nowrap|nw-resize|oblique|open-quote|outset|outside|overline|padding-box|page|pointer|pre-line|pre-wrap|pre|preserve-3d|progress|relative|repeat-x|repeat-y|repeat|replaced|reset-size|ridge|right|round|row-resize|rtl|s-resize|scroll|se-resize|separate|slice|small-caps|small-caption|solid|space|square|start|static|status-bar|step-end|step-start|steps|stretch|strict|sub|super|sw-resize|table-caption|table-cell|table-column-group|table-column|table-footer-group|table-header-group|table-row-group|table-row|table|tb-rl|text-after-edge|text-before-edge|text-bottom|text-size|text-top|text|thick|thin|transparent|underline|upper-alpha|upper-latin|upper-roman|uppercase|use-script|vertical-ideographic|vertical-text|visible|w-resize|wait|whitespace|z-index|zero|zoom";
var supportConstantColor = exports.supportConstantColor = "aliceblue|antiquewhite|aqua|aquamarine|azure|beige|bisque|black|blanchedalmond|blue|blueviolet|brown|burlywood|cadetblue|chartreuse|chocolate|coral|cornflowerblue|cornsilk|crimson|cyan|darkblue|darkcyan|darkgoldenrod|darkgray|darkgreen|darkgrey|darkkhaki|darkmagenta|darkolivegreen|darkorange|darkorchid|darkred|darksalmon|darkseagreen|darkslateblue|darkslategray|darkslategrey|darkturquoise|darkviolet|deeppink|deepskyblue|dimgray|dimgrey|dodgerblue|firebrick|floralwhite|forestgreen|fuchsia|gainsboro|ghostwhite|gold|goldenrod|gray|green|greenyellow|grey|honeydew|hotpink|indianred|indigo|ivory|khaki|lavender|lavenderblush|lawngreen|lemonchiffon|lightblue|lightcoral|lightcyan|lightgoldenrodyellow|lightgray|lightgreen|lightgrey|lightpink|lightsalmon|lightseagreen|lightskyblue|lightslategray|lightslategrey|lightsteelblue|lightyellow|lime|limegreen|linen|magenta|maroon|mediumaquamarine|mediumblue|mediumorchid|mediumpurple|mediumseagreen|mediumslateblue|mediumspringgreen|mediumturquoise|mediumvioletred|midnightblue|mintcream|mistyrose|moccasin|navajowhite|navy|oldlace|olive|olivedrab|orange|orangered|orchid|palegoldenrod|palegreen|paleturquoise|palevioletred|papayawhip|peachpuff|peru|pink|plum|powderblue|purple|rebeccapurple|red|rosybrown|royalblue|saddlebrown|salmon|sandybrown|seagreen|seashell|sienna|silver|skyblue|slateblue|slategray|slategrey|snow|springgreen|steelblue|tan|teal|thistle|tomato|turquoise|violet|wheat|white|whitesmoke|yellow|yellowgreen";
var supportConstantFonts = exports.supportConstantFonts = "arial|century|comic|courier|cursive|fantasy|garamond|georgia|helvetica|impact|lucida|symbol|system|tahoma|times|trebuchet|utopia|verdana|webdings|sans-serif|serif|monospace";

var numRe = exports.numRe = "\\-?(?:(?:[0-9]+(?:\\.[0-9]+)?)|(?:\\.[0-9]+))";
var pseudoElements = exports.pseudoElements = "(\\:+)\\b(after|before|first-letter|first-line|moz-selection|selection)\\b";
var pseudoClasses  = exports.pseudoClasses =  "(:)\\b(active|checked|disabled|empty|enabled|first-child|first-of-type|focus|hover|indeterminate|invalid|last-child|last-of-type|link|not|nth-child|nth-last-child|nth-last-of-type|nth-of-type|only-child|only-of-type|required|root|target|valid|visited)\\b";

var CssHighlightRules = function() {

    var keywordMapper = this.createKeywordMapper({
        "support.function": supportFunction,
        "support.constant": supportConstant,
        "support.type": supportType,
        "support.constant.color": supportConstantColor,
        "support.constant.fonts": supportConstantFonts
    }, "text", true);

    this.$rules = {
        "start" : [{
            include : ["strings", "url", "comments"]
        }, {
            token: "paren.lparen",
            regex: "\\{",
            next:  "ruleset"
        }, {
            token: "paren.rparen",
            regex: "\\}"
        }, {
            token: "string",
            regex: "@(?!viewport)",
            next:  "media"
        }, {
            token: "keyword",
            regex: "#[a-z0-9-_]+"
        }, {
            token: "keyword",
            regex: "%"
        }, {
            token: "variable",
            regex: "\\.[a-z0-9-_]+"
        }, {
            token: "string",
            regex: ":[a-z0-9-_]+"
        }, {
            token : "constant.numeric",
            regex : numRe
        }, {
            token: "constant",
            regex: "[a-z0-9-_]+"
        }, {
            caseInsensitive: true
        }],

        "media": [{
            include : ["strings", "url", "comments"]
        }, {
            token: "paren.lparen",
            regex: "\\{",
            next:  "start"
        }, {
            token: "paren.rparen",
            regex: "\\}",
            next:  "start"
        }, {
            token: "string",
            regex: ";",
            next:  "start"
        }, {
            token: "keyword",
            regex: "(?:media|supports|document|charset|import|namespace|media|supports|document"
                + "|page|font|keyframes|viewport|counter-style|font-feature-values"
                + "|swash|ornaments|annotation|stylistic|styleset|character-variant)"
        }],

        "comments" : [{
            token: "comment", // multi line comment
            regex: "\\/\\*",
            push: [{
                token : "comment",
                regex : "\\*\\/",
                next : "pop"
            }, {
                defaultToken : "comment"
            }]
        }],

        "ruleset" : [{
            regex : "-(webkit|ms|moz|o)-",
            token : "text"
        }, {
            token : "punctuation.operator",
            regex : "[:;]"
        }, {
            token : "paren.rparen",
            regex : "\\}",
            next : "start"
        }, {
            include : ["strings", "url", "comments"]
        }, {
            token : ["constant.numeric", "keyword"],
            regex : "(" + numRe + ")(ch|cm|deg|em|ex|fr|gd|grad|Hz|in|kHz|mm|ms|pc|pt|px|rad|rem|s|turn|vh|vmax|vmin|vm|vw|%)"
        }, {
            token : "constant.numeric",
            regex : numRe
        }, {
            token : "constant.numeric",  // hex6 color
            regex : "#[a-f0-9]{6}"
        }, {
            token : "constant.numeric", // hex3 color
            regex : "#[a-f0-9]{3}"
        }, {
            token : ["punctuation", "entity.other.attribute-name.pseudo-element.css"],
            regex : pseudoElements
        }, {
            token : ["punctuation", "entity.other.attribute-name.pseudo-class.css"],
            regex : pseudoClasses
        }, {
            include: "url"
        }, {
            token : keywordMapper,
            regex : "\\-?[a-zA-Z_][a-zA-Z0-9_\\-]*"
        }, {
            caseInsensitive: true
        }],

        url: [{
            token : "support.function",
            regex : "(?:url(:?-prefix)?|domain|regexp)\\(",
            push: [{
                token : "support.function",
                regex : "\\)",
                next : "pop"
            }, {
                defaultToken: "string"
            }]
        }],

        strings: [{
            token : "string.start",
            regex : "'",
            push : [{
                token : "string.end",
                regex : "'|$",
                next: "pop"
            }, {
                include : "escapes"
            }, {
                token : "constant.language.escape",
                regex : /\\$/,
                consumeLineEnd: true
            }, {
                defaultToken: "string"
            }]
        }, {
            token : "string.start",
            regex : '"',
            push : [{
                token : "string.end",
                regex : '"|$',
                next: "pop"
            }, {
                include : "escapes"
            }, {
                token : "constant.language.escape",
                regex : /\\$/,
                consumeLineEnd: true
            }, {
                defaultToken: "string"
            }]
        }],
        escapes: [{
            token : "constant.language.escape",
            regex : /\\([a-fA-F\d]{1,6}|[^a-fA-F\d])/
        }]

    };

    this.normalizeRules();
};

oop.inherits(CssHighlightRules, TextHighlightRules);

exports.CssHighlightRules = CssHighlightRules;

});

define("ace/mode/css_completions",["require","exports","module"], function(require, exports, module) {
"use strict";

var propertyMap = {
    "background": {"#$0": 1},
    "background-color": {"#$0": 1, "transparent": 1, "fixed": 1},
    "background-image": {"url('/$0')": 1},
    "background-repeat": {"repeat": 1, "repeat-x": 1, "repeat-y": 1, "no-repeat": 1, "inherit": 1},
    "background-position": {"bottom":2, "center":2, "left":2, "right":2, "top":2, "inherit":2},
    "background-attachment": {"scroll": 1, "fixed": 1},
    "background-size": {"cover": 1, "contain": 1},
    "background-clip": {"border-box": 1, "padding-box": 1, "content-box": 1},
    "background-origin": {"border-box": 1, "padding-box": 1, "content-box": 1},
    "border": {"solid $0": 1, "dashed $0": 1, "dotted $0": 1, "#$0": 1},
    "border-color": {"#$0": 1},
    "border-style": {"solid":2, "dashed":2, "dotted":2, "double":2, "groove":2, "hidden":2, "inherit":2, "inset":2, "none":2, "outset":2, "ridged":2},
    "border-collapse": {"collapse": 1, "separate": 1},
    "bottom": {"px": 1, "em": 1, "%": 1},
    "clear": {"left": 1, "right": 1, "both": 1, "none": 1},
    "color": {"#$0": 1, "rgb(#$00,0,0)": 1},
    "cursor": {"default": 1, "pointer": 1, "move": 1, "text": 1, "wait": 1, "help": 1, "progress": 1, "n-resize": 1, "ne-resize": 1, "e-resize": 1, "se-resize": 1, "s-resize": 1, "sw-resize": 1, "w-resize": 1, "nw-resize": 1},
    "display": {"none": 1, "block": 1, "inline": 1, "inline-block": 1, "table-cell": 1},
    "empty-cells": {"show": 1, "hide": 1},
    "float": {"left": 1, "right": 1, "none": 1},
    "font-family": {"Arial":2,"Comic Sans MS":2,"Consolas":2,"Courier New":2,"Courier":2,"Georgia":2,"Monospace":2,"Sans-Serif":2, "Segoe UI":2,"Tahoma":2,"Times New Roman":2,"Trebuchet MS":2,"Verdana": 1},
    "font-size": {"px": 1, "em": 1, "%": 1},
    "font-weight": {"bold": 1, "normal": 1},
    "font-style": {"italic": 1, "normal": 1},
    "font-variant": {"normal": 1, "small-caps": 1},
    "height": {"px": 1, "em": 1, "%": 1},
    "left": {"px": 1, "em": 1, "%": 1},
    "letter-spacing": {"normal": 1},
    "line-height": {"normal": 1},
    "list-style-type": {"none": 1, "disc": 1, "circle": 1, "square": 1, "decimal": 1, "decimal-leading-zero": 1, "lower-roman": 1, "upper-roman": 1, "lower-greek": 1, "lower-latin": 1, "upper-latin": 1, "georgian": 1, "lower-alpha": 1, "upper-alpha": 1},
    "margin": {"px": 1, "em": 1, "%": 1},
    "margin-right": {"px": 1, "em": 1, "%": 1},
    "margin-left": {"px": 1, "em": 1, "%": 1},
    "margin-top": {"px": 1, "em": 1, "%": 1},
    "margin-bottom": {"px": 1, "em": 1, "%": 1},
    "max-height": {"px": 1, "em": 1, "%": 1},
    "max-width": {"px": 1, "em": 1, "%": 1},
    "min-height": {"px": 1, "em": 1, "%": 1},
    "min-width": {"px": 1, "em": 1, "%": 1},
    "overflow": {"hidden": 1, "visible": 1, "auto": 1, "scroll": 1},
    "overflow-x": {"hidden": 1, "visible": 1, "auto": 1, "scroll": 1},
    "overflow-y": {"hidden": 1, "visible": 1, "auto": 1, "scroll": 1},
    "padding": {"px": 1, "em": 1, "%": 1},
    "padding-top": {"px": 1, "em": 1, "%": 1},
    "padding-right": {"px": 1, "em": 1, "%": 1},
    "padding-bottom": {"px": 1, "em": 1, "%": 1},
    "padding-left": {"px": 1, "em": 1, "%": 1},
    "page-break-after": {"auto": 1, "always": 1, "avoid": 1, "left": 1, "right": 1},
    "page-break-before": {"auto": 1, "always": 1, "avoid": 1, "left": 1, "right": 1},
    "position": {"absolute": 1, "relative": 1, "fixed": 1, "static": 1},
    "right": {"px": 1, "em": 1, "%": 1},
    "table-layout": {"fixed": 1, "auto": 1},
    "text-decoration": {"none": 1, "underline": 1, "line-through": 1, "blink": 1},
    "text-align": {"left": 1, "right": 1, "center": 1, "justify": 1},
    "text-transform": {"capitalize": 1, "uppercase": 1, "lowercase": 1, "none": 1},
    "top": {"px": 1, "em": 1, "%": 1},
    "vertical-align": {"top": 1, "bottom": 1},
    "visibility": {"hidden": 1, "visible": 1},
    "white-space": {"nowrap": 1, "normal": 1, "pre": 1, "pre-line": 1, "pre-wrap": 1},
    "width": {"px": 1, "em": 1, "%": 1},
    "word-spacing": {"normal": 1},
    "filter": {"alpha(opacity=$0100)": 1},

    "text-shadow": {"$02px 2px 2px #777": 1},
    "text-overflow": {"ellipsis-word": 1, "clip": 1, "ellipsis": 1},
    "-moz-border-radius": 1,
    "-moz-border-radius-topright": 1,
    "-moz-border-radius-bottomright": 1,
    "-moz-border-radius-topleft": 1,
    "-moz-border-radius-bottomleft": 1,
    "-webkit-border-radius": 1,
    "-webkit-border-top-right-radius": 1,
    "-webkit-border-top-left-radius": 1,
    "-webkit-border-bottom-right-radius": 1,
    "-webkit-border-bottom-left-radius": 1,
    "-moz-box-shadow": 1,
    "-webkit-box-shadow": 1,
    "transform": {"rotate($00deg)": 1, "skew($00deg)": 1},
    "-moz-transform": {"rotate($00deg)": 1, "skew($00deg)": 1},
    "-webkit-transform": {"rotate($00deg)": 1, "skew($00deg)": 1 }
};

var CssCompletions = function() {

};

(function() {

    this.completionsDefined = false;

    this.defineCompletions = function() {
        if (document) {
            var style = document.createElement('c').style;

            for (var i in style) {
                if (typeof style[i] !== 'string')
                    continue;

                var name = i.replace(/[A-Z]/g, function(x) {
                    return '-' + x.toLowerCase();
                });

                if (!propertyMap.hasOwnProperty(name))
                    propertyMap[name] = 1;
            }
        }

        this.completionsDefined = true;
    };

    this.getCompletions = function(state, session, pos, prefix) {
        if (!this.completionsDefined) {
            this.defineCompletions();
        }

        if (state==='ruleset' || session.$mode.$id == "ace/mode/scss") {
            var line = session.getLine(pos.row).substr(0, pos.column);
            if (/:[^;]+$/.test(line)) {
                /([\w\-]+):[^:]*$/.test(line);

                return this.getPropertyValueCompletions(state, session, pos, prefix);
            } else {
                return this.getPropertyCompletions(state, session, pos, prefix);
            }
        }

        return [];
    };

    this.getPropertyCompletions = function(state, session, pos, prefix) {
        var properties = Object.keys(propertyMap);
        return properties.map(function(property){
            return {
                caption: property,
                snippet: property + ': $0;',
                meta: "property",
                score: 1000000
            };
        });
    };

    this.getPropertyValueCompletions = function(state, session, pos, prefix) {
        var line = session.getLine(pos.row).substr(0, pos.column);
        var property = (/([\w\-]+):[^:]*$/.exec(line) || {})[1];

        if (!property)
            return [];
        var values = [];
        if (property in propertyMap && typeof propertyMap[property] === "object") {
            values = Object.keys(propertyMap[property]);
        }
        return values.map(function(value){
            return {
                caption: value,
                snippet: value,
                meta: "property value",
                score: 1000000
            };
        });
    };

}).call(CssCompletions.prototype);

exports.CssCompletions = CssCompletions;
});

define("ace/mode/behaviour/css",["require","exports","module","ace/lib/oop","ace/mode/behaviour","ace/mode/behaviour/cstyle","ace/token_iterator"], function(require, exports, module) {
"use strict";

var oop = require("../../lib/oop");
var Behaviour = require("../behaviour").Behaviour;
var CstyleBehaviour = require("./cstyle").CstyleBehaviour;
var TokenIterator = require("../../token_iterator").TokenIterator;

var CssBehaviour = function () {

    this.inherit(CstyleBehaviour);

    this.add("colon", "insertion", function (state, action, editor, session, text) {
        if (text === ':' && editor.selection.isEmpty()) {
            var cursor = editor.getCursorPosition();
            var iterator = new TokenIterator(session, cursor.row, cursor.column);
            var token = iterator.getCurrentToken();
            if (token && token.value.match(/\s+/)) {
                token = iterator.stepBackward();
            }
            if (token && token.type === 'support.type') {
                var line = session.doc.getLine(cursor.row);
                var rightChar = line.substring(cursor.column, cursor.column + 1);
                if (rightChar === ':') {
                    return {
                       text: '',
                       selection: [1, 1]
                    };
                }
                if (/^(\s+[^;]|\s*$)/.test(line.substring(cursor.column))) {
                    return {
                       text: ':;',
                       selection: [1, 1]
                    };
                }
            }
        }
    });

    this.add("colon", "deletion", function (state, action, editor, session, range) {
        var selected = session.doc.getTextRange(range);
        if (!range.isMultiLine() && selected === ':') {
            var cursor = editor.getCursorPosition();
            var iterator = new TokenIterator(session, cursor.row, cursor.column);
            var token = iterator.getCurrentToken();
            if (token && token.value.match(/\s+/)) {
                token = iterator.stepBackward();
            }
            if (token && token.type === 'support.type') {
                var line = session.doc.getLine(range.start.row);
                var rightChar = line.substring(range.end.column, range.end.column + 1);
                if (rightChar === ';') {
                    range.end.column ++;
                    return range;
                }
            }
        }
    });

    this.add("semicolon", "insertion", function (state, action, editor, session, text) {
        if (text === ';' && editor.selection.isEmpty()) {
            var cursor = editor.getCursorPosition();
            var line = session.doc.getLine(cursor.row);
            var rightChar = line.substring(cursor.column, cursor.column + 1);
            if (rightChar === ';') {
                return {
                   text: '',
                   selection: [1, 1]
                };
            }
        }
    });

    this.add("!important", "insertion", function (state, action, editor, session, text) {
        if (text === '!' && editor.selection.isEmpty()) {
            var cursor = editor.getCursorPosition();
            var line = session.doc.getLine(cursor.row);

            if (/^\s*(;|}|$)/.test(line.substring(cursor.column))) {
                return {
                    text: '!important',
                    selection: [10, 10]
                };
            }
        }
    });

};
oop.inherits(CssBehaviour, CstyleBehaviour);

exports.CssBehaviour = CssBehaviour;
});

define("ace/mode/css",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/css_highlight_rules","ace/mode/matching_brace_outdent","ace/worker/worker_client","ace/mode/css_completions","ace/mode/behaviour/css","ace/mode/folding/cstyle"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var CssHighlightRules = require("./css_highlight_rules").CssHighlightRules;
var MatchingBraceOutdent = require("./matching_brace_outdent").MatchingBraceOutdent;
var WorkerClient = require("../worker/worker_client").WorkerClient;
var CssCompletions = require("./css_completions").CssCompletions;
var CssBehaviour = require("./behaviour/css").CssBehaviour;
var CStyleFoldMode = require("./folding/cstyle").FoldMode;

var Mode = function() {
    this.HighlightRules = CssHighlightRules;
    this.$outdent = new MatchingBraceOutdent();
    this.$behaviour = new CssBehaviour();
    this.$completer = new CssCompletions();
    this.foldingRules = new CStyleFoldMode();
};
oop.inherits(Mode, TextMode);

(function() {

    this.foldingRules = "cStyle";
    this.blockComment = {start: "/*", end: "*/"};

    this.getNextLineIndent = function(state, line, tab) {
        var indent = this.$getIndent(line);
        var tokens = this.getTokenizer().getLineTokens(line, state).tokens;
        if (tokens.length && tokens[tokens.length-1].type == "comment") {
            return indent;
        }

        var match = line.match(/^.*\{\s*$/);
        if (match) {
            indent += tab;
        }

        return indent;
    };

    this.checkOutdent = function(state, line, input) {
        return this.$outdent.checkOutdent(line, input);
    };

    this.autoOutdent = function(state, doc, row) {
        this.$outdent.autoOutdent(doc, row);
    };

    this.getCompletions = function(state, session, pos, prefix) {
        return this.$completer.getCompletions(state, session, pos, prefix);
    };

    this.createWorker = function(session) {
        var worker = new WorkerClient(["ace"], "ace/mode/css_worker", "Worker");
        worker.attachToDocument(session.getDocument());

        worker.on("annotate", function(e) {
            session.setAnnotations(e.data);
        });

        worker.on("terminate", function() {
            session.clearAnnotations();
        });

        return worker;
    };

    this.$id = "ace/mode/css";
}).call(Mode.prototype);

exports.Mode = Mode;

});

define("ace/mode/html_highlight_rules",["require","exports","module","ace/lib/oop","ace/lib/lang","ace/mode/css_highlight_rules","ace/mode/javascript_highlight_rules","ace/mode/xml_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var lang = require("../lib/lang");
var CssHighlightRules = require("./css_highlight_rules").CssHighlightRules;
var JavaScriptHighlightRules = require("./javascript_highlight_rules").JavaScriptHighlightRules;
var XmlHighlightRules = require("./xml_highlight_rules").XmlHighlightRules;

var tagMap = lang.createMap({
    a           : 'anchor',
    button 	    : 'form',
    form        : 'form',
    img         : 'image',
    input       : 'form',
    label       : 'form',
    option      : 'form',
    script      : 'script',
    select      : 'form',
    textarea    : 'form',
    style       : 'style',
    table       : 'table',
    tbody       : 'table',
    td          : 'table',
    tfoot       : 'table',
    th          : 'table',
    tr          : 'table'
});

var HtmlHighlightRules = function() {
    XmlHighlightRules.call(this);

    this.addRules({
        attributes: [{
            include : "tag_whitespace"
        }, {
            token : "entity.other.attribute-name.xml",
            regex : "[-_a-zA-Z0-9:.]+"
        }, {
            token : "keyword.operator.attribute-equals.xml",
            regex : "=",
            push : [{
                include: "tag_whitespace"
            }, {
                token : "string.unquoted.attribute-value.html",
                regex : "[^<>='\"`\\s]+",
                next : "pop"
            }, {
                token : "empty",
                regex : "",
                next : "pop"
            }]
        }, {
            include : "attribute_value"
        }],
        tag: [{
            token : function(start, tag) {
                var group = tagMap[tag];
                return ["meta.tag.punctuation." + (start == "<" ? "" : "end-") + "tag-open.xml",
                    "meta.tag" + (group ? "." + group : "") + ".tag-name.xml"];
            },
            regex : "(</?)([-_a-zA-Z0-9:.]+)",
            next: "tag_stuff"
        }],
        tag_stuff: [
            {include : "attributes"},
            {token : "meta.tag.punctuation.tag-close.xml", regex : "/?>", next : "start"}
        ]
    });

    this.embedTagRules(CssHighlightRules, "css-", "style");
    this.embedTagRules(new JavaScriptHighlightRules({jsx: false}).getRules(), "js-", "script");

    if (this.constructor === HtmlHighlightRules)
        this.normalizeRules();
};

oop.inherits(HtmlHighlightRules, XmlHighlightRules);

exports.HtmlHighlightRules = HtmlHighlightRules;
});

define("ace/mode/folding/mixed",["require","exports","module","ace/lib/oop","ace/mode/folding/fold_mode"], function(require, exports, module) {
"use strict";

var oop = require("../../lib/oop");
var BaseFoldMode = require("./fold_mode").FoldMode;

var FoldMode = exports.FoldMode = function(defaultMode, subModes) {
    this.defaultMode = defaultMode;
    this.subModes = subModes;
};
oop.inherits(FoldMode, BaseFoldMode);

(function() {


    this.$getMode = function(state) {
        if (typeof state != "string") 
            state = state[0];
        for (var key in this.subModes) {
            if (state.indexOf(key) === 0)
                return this.subModes[key];
        }
        return null;
    };
    
    this.$tryMode = function(state, session, foldStyle, row) {
        var mode = this.$getMode(state);
        return (mode ? mode.getFoldWidget(session, foldStyle, row) : "");
    };

    this.getFoldWidget = function(session, foldStyle, row) {
        return (
            this.$tryMode(session.getState(row-1), session, foldStyle, row) ||
            this.$tryMode(session.getState(row), session, foldStyle, row) ||
            this.defaultMode.getFoldWidget(session, foldStyle, row)
        );
    };

    this.getFoldWidgetRange = function(session, foldStyle, row) {
        var mode = this.$getMode(session.getState(row-1));
        
        if (!mode || !mode.getFoldWidget(session, foldStyle, row))
            mode = this.$getMode(session.getState(row));
        
        if (!mode || !mode.getFoldWidget(session, foldStyle, row))
            mode = this.defaultMode;
        
        return mode.getFoldWidgetRange(session, foldStyle, row);
    };

}).call(FoldMode.prototype);

});

define("ace/mode/folding/html",["require","exports","module","ace/lib/oop","ace/mode/folding/mixed","ace/mode/folding/xml","ace/mode/folding/cstyle"], function(require, exports, module) {
"use strict";

var oop = require("../../lib/oop");
var MixedFoldMode = require("./mixed").FoldMode;
var XmlFoldMode = require("./xml").FoldMode;
var CStyleFoldMode = require("./cstyle").FoldMode;

var FoldMode = exports.FoldMode = function(voidElements, optionalTags) {
    MixedFoldMode.call(this, new XmlFoldMode(voidElements, optionalTags), {
        "js-": new CStyleFoldMode(),
        "css-": new CStyleFoldMode()
    });
};

oop.inherits(FoldMode, MixedFoldMode);

});

define("ace/mode/html_completions",["require","exports","module","ace/token_iterator"], function(require, exports, module) {
"use strict";

var TokenIterator = require("../token_iterator").TokenIterator;

var commonAttributes = [
    "accesskey",
    "class",
    "contenteditable",
    "contextmenu",
    "dir",
    "draggable",
    "dropzone",
    "hidden",
    "id",
    "inert",
    "itemid",
    "itemprop",
    "itemref",
    "itemscope",
    "itemtype",
    "lang",
    "spellcheck",
    "style",
    "tabindex",
    "title",
    "translate"
];

var eventAttributes = [
    "onabort",
    "onblur",
    "oncancel",
    "oncanplay",
    "oncanplaythrough",
    "onchange",
    "onclick",
    "onclose",
    "oncontextmenu",
    "oncuechange",
    "ondblclick",
    "ondrag",
    "ondragend",
    "ondragenter",
    "ondragleave",
    "ondragover",
    "ondragstart",
    "ondrop",
    "ondurationchange",
    "onemptied",
    "onended",
    "onerror",
    "onfocus",
    "oninput",
    "oninvalid",
    "onkeydown",
    "onkeypress",
    "onkeyup",
    "onload",
    "onloadeddata",
    "onloadedmetadata",
    "onloadstart",
    "onmousedown",
    "onmousemove",
    "onmouseout",
    "onmouseover",
    "onmouseup",
    "onmousewheel",
    "onpause",
    "onplay",
    "onplaying",
    "onprogress",
    "onratechange",
    "onreset",
    "onscroll",
    "onseeked",
    "onseeking",
    "onselect",
    "onshow",
    "onstalled",
    "onsubmit",
    "onsuspend",
    "ontimeupdate",
    "onvolumechange",
    "onwaiting"
];

var globalAttributes = commonAttributes.concat(eventAttributes);

var attributeMap = {
    "a": {"href": 1, "target": {"_blank": 1, "top": 1}, "ping": 1, "rel": {"nofollow": 1, "alternate": 1, "author": 1, "bookmark": 1, "help": 1, "license": 1, "next": 1, "noreferrer": 1, "prefetch": 1, "prev": 1, "search": 1, "tag": 1}, "media": 1, "hreflang": 1, "type": 1},
    "abbr": {},
    "address": {},
    "area": {"shape": 1, "coords": 1, "href": 1, "hreflang": 1, "alt": 1, "target": 1, "media": 1, "rel": 1, "ping": 1, "type": 1},
    "article": {"pubdate": 1},
    "aside": {},
    "audio": {"src": 1, "autobuffer": 1, "autoplay": {"autoplay": 1}, "loop": {"loop": 1}, "controls": {"controls": 1}, "muted": {"muted": 1}, "preload": {"auto": 1, "metadata": 1, "none": 1 }},
    "b": {},
    "base": {"href": 1, "target": 1},
    "bdi": {},
    "bdo": {},
    "blockquote": {"cite": 1},
    "body": {"onafterprint": 1, "onbeforeprint": 1, "onbeforeunload": 1, "onhashchange": 1, "onmessage": 1, "onoffline": 1, "onpopstate": 1, "onredo": 1, "onresize": 1, "onstorage": 1, "onundo": 1, "onunload": 1},
    "br": {},
    "button": {"autofocus": 1, "disabled": {"disabled": 1}, "form": 1, "formaction": 1, "formenctype": 1, "formmethod": 1, "formnovalidate": 1, "formtarget": 1, "name": 1, "value": 1, "type": {"button": 1, "submit": 1}},
    "canvas": {"width": 1, "height": 1},
    "caption": {},
    "cite": {},
    "code": {},
    "col": {"span": 1},
    "colgroup": {"span": 1},
    "command": {"type": 1, "label": 1, "icon": 1, "disabled": 1, "checked": 1, "radiogroup": 1, "command": 1},
    "data": {},
    "datalist": {},
    "dd": {},
    "del": {"cite": 1, "datetime": 1},
    "details": {"open": 1},
    "dfn": {},
    "dialog": {"open": 1},
    "div": {},
    "dl": {},
    "dt": {},
    "em": {},
    "embed": {"src": 1, "height": 1, "width": 1, "type": 1},
    "fieldset": {"disabled": 1, "form": 1, "name": 1},
    "figcaption": {},
    "figure": {},
    "footer": {},
    "form": {"accept-charset": 1, "action": 1, "autocomplete": 1, "enctype": {"multipart/form-data": 1, "application/x-www-form-urlencoded": 1}, "method": {"get": 1, "post": 1}, "name": 1, "novalidate": 1, "target": {"_blank": 1, "top": 1}},
    "h1": {},
    "h2": {},
    "h3": {},
    "h4": {},
    "h5": {},
    "h6": {},
    "head": {},
    "header": {},
    "hr": {},
    "html": {"manifest": 1},
    "i": {},
    "iframe": {"name": 1, "src": 1, "height": 1, "width": 1, "sandbox": {"allow-same-origin": 1, "allow-top-navigation": 1, "allow-forms": 1, "allow-scripts": 1}, "seamless": {"seamless": 1}},
    "img": {"alt": 1, "src": 1, "height": 1, "width": 1, "usemap": 1, "ismap": 1},
    "input": {
        "type": {"text": 1, "password": 1, "hidden": 1, "checkbox": 1, "submit": 1, "radio": 1, "file": 1, "button": 1, "reset": 1, "image": 31, "color": 1, "date": 1, "datetime": 1, "datetime-local": 1, "email": 1, "month": 1, "number": 1, "range": 1, "search": 1, "tel": 1, "time": 1, "url": 1, "week": 1},
        "accept": 1, "alt": 1, "autocomplete": {"on": 1, "off": 1}, "autofocus": {"autofocus": 1}, "checked": {"checked": 1}, "disabled": {"disabled": 1}, "form": 1, "formaction": 1, "formenctype": {"application/x-www-form-urlencoded": 1, "multipart/form-data": 1, "text/plain": 1}, "formmethod": {"get": 1, "post": 1}, "formnovalidate": {"formnovalidate": 1}, "formtarget": {"_blank": 1, "_self": 1, "_parent": 1, "_top": 1}, "height": 1, "list": 1, "max": 1, "maxlength": 1, "min": 1, "multiple": {"multiple": 1}, "name": 1, "pattern": 1, "placeholder": 1, "readonly": {"readonly": 1}, "required": {"required": 1}, "size": 1, "src": 1, "step": 1, "width": 1, "files": 1, "value": 1},
    "ins": {"cite": 1, "datetime": 1},
    "kbd": {},
    "keygen": {"autofocus": 1, "challenge": {"challenge": 1}, "disabled": {"disabled": 1}, "form": 1, "keytype": {"rsa": 1, "dsa": 1, "ec": 1}, "name": 1},
    "label": {"form": 1, "for": 1},
    "legend": {},
    "li": {"value": 1},
    "link": {"href": 1, "hreflang": 1, "rel": {"stylesheet": 1, "icon": 1}, "media": {"all": 1, "screen": 1, "print": 1}, "type": {"text/css": 1, "image/png": 1, "image/jpeg": 1, "image/gif": 1}, "sizes": 1},
    "main": {},
    "map": {"name": 1},
    "mark": {},
    "math": {},
    "menu": {"type": 1, "label": 1},
    "meta": {"http-equiv": {"content-type": 1}, "name": {"description": 1, "keywords": 1}, "content": {"text/html; charset=UTF-8": 1}, "charset": 1},
    "meter": {"value": 1, "min": 1, "max": 1, "low": 1, "high": 1, "optimum": 1},
    "nav": {},
    "noscript": {"href": 1},
    "object": {"param": 1, "data": 1, "type": 1, "height" : 1, "width": 1, "usemap": 1, "name": 1, "form": 1, "classid": 1},
    "ol": {"start": 1, "reversed": 1},
    "optgroup": {"disabled": 1, "label": 1},
    "option": {"disabled": 1, "selected": 1, "label": 1, "value": 1},
    "output": {"for": 1, "form": 1, "name": 1},
    "p": {},
    "param": {"name": 1, "value": 1},
    "pre": {},
    "progress": {"value": 1, "max": 1},
    "q": {"cite": 1},
    "rp": {},
    "rt": {},
    "ruby": {},
    "s": {},
    "samp": {},
    "script": {"charset": 1, "type": {"text/javascript": 1}, "src": 1, "defer": 1, "async": 1},
    "select": {"autofocus": 1, "disabled": 1, "form": 1, "multiple": {"multiple": 1}, "name": 1, "size": 1, "readonly":{"readonly": 1}},
    "small": {},
    "source": {"src": 1, "type": 1, "media": 1},
    "span": {},
    "strong": {},
    "style": {"type": 1, "media": {"all": 1, "screen": 1, "print": 1}, "scoped": 1},
    "sub": {},
    "sup": {},
    "svg": {},
    "table": {"summary": 1},
    "tbody": {},
    "td": {"headers": 1, "rowspan": 1, "colspan": 1},
    "textarea": {"autofocus": {"autofocus": 1}, "disabled": {"disabled": 1}, "form": 1, "maxlength": 1, "name": 1, "placeholder": 1, "readonly": {"readonly": 1}, "required": {"required": 1}, "rows": 1, "cols": 1, "wrap": {"on": 1, "off": 1, "hard": 1, "soft": 1}},
    "tfoot": {},
    "th": {"headers": 1, "rowspan": 1, "colspan": 1, "scope": 1},
    "thead": {},
    "time": {"datetime": 1},
    "title": {},
    "tr": {},
    "track": {"kind": 1, "src": 1, "srclang": 1, "label": 1, "default": 1},
    "section": {},
    "summary": {},
    "u": {},
    "ul": {},
    "var": {},
    "video": {"src": 1, "autobuffer": 1, "autoplay": {"autoplay": 1}, "loop": {"loop": 1}, "controls": {"controls": 1}, "width": 1, "height": 1, "poster": 1, "muted": {"muted": 1}, "preload": {"auto": 1, "metadata": 1, "none": 1}},
    "wbr": {}
};

var elements = Object.keys(attributeMap);

function is(token, type) {
    return token.type.lastIndexOf(type + ".xml") > -1;
}

function findTagName(session, pos) {
    var iterator = new TokenIterator(session, pos.row, pos.column);
    var token = iterator.getCurrentToken();
    while (token && !is(token, "tag-name")){
        token = iterator.stepBackward();
    }
    if (token)
        return token.value;
}

function findAttributeName(session, pos) {
    var iterator = new TokenIterator(session, pos.row, pos.column);
    var token = iterator.getCurrentToken();
    while (token && !is(token, "attribute-name")){
        token = iterator.stepBackward();
    }
    if (token)
        return token.value;
}

var HtmlCompletions = function() {

};

(function() {

    this.getCompletions = function(state, session, pos, prefix) {
        var token = session.getTokenAt(pos.row, pos.column);

        if (!token)
            return [];
        if (is(token, "tag-name") || is(token, "tag-open") || is(token, "end-tag-open"))
            return this.getTagCompletions(state, session, pos, prefix);
        if (is(token, "tag-whitespace") || is(token, "attribute-name"))
            return this.getAttributeCompletions(state, session, pos, prefix);
        if (is(token, "attribute-value"))
            return this.getAttributeValueCompletions(state, session, pos, prefix);
        var line = session.getLine(pos.row).substr(0, pos.column);
        if (/&[a-z]*$/i.test(line))
            return this.getHTMLEntityCompletions(state, session, pos, prefix);

        return [];
    };

    this.getTagCompletions = function(state, session, pos, prefix) {
        return elements.map(function(element){
            return {
                value: element,
                meta: "tag",
                score: 1000000
            };
        });
    };

    this.getAttributeCompletions = function(state, session, pos, prefix) {
        var tagName = findTagName(session, pos);
        if (!tagName)
            return [];
        var attributes = globalAttributes;
        if (tagName in attributeMap) {
            attributes = attributes.concat(Object.keys(attributeMap[tagName]));
        }
        return attributes.map(function(attribute){
            return {
                caption: attribute,
                snippet: attribute + '="$0"',
                meta: "attribute",
                score: 1000000
            };
        });
    };

    this.getAttributeValueCompletions = function(state, session, pos, prefix) {
        var tagName = findTagName(session, pos);
        var attributeName = findAttributeName(session, pos);
        
        if (!tagName)
            return [];
        var values = [];
        if (tagName in attributeMap && attributeName in attributeMap[tagName] && typeof attributeMap[tagName][attributeName] === "object") {
            values = Object.keys(attributeMap[tagName][attributeName]);
        }
        return values.map(function(value){
            return {
                caption: value,
                snippet: value,
                meta: "attribute value",
                score: 1000000
            };
        });
    };

    this.getHTMLEntityCompletions = function(state, session, pos, prefix) {
        var values = ['Aacute;', 'aacute;', 'Acirc;', 'acirc;', 'acute;', 'AElig;', 'aelig;', 'Agrave;', 'agrave;', 'alefsym;', 'Alpha;', 'alpha;', 'amp;', 'and;', 'ang;', 'Aring;', 'aring;', 'asymp;', 'Atilde;', 'atilde;', 'Auml;', 'auml;', 'bdquo;', 'Beta;', 'beta;', 'brvbar;', 'bull;', 'cap;', 'Ccedil;', 'ccedil;', 'cedil;', 'cent;', 'Chi;', 'chi;', 'circ;', 'clubs;', 'cong;', 'copy;', 'crarr;', 'cup;', 'curren;', 'Dagger;', 'dagger;', 'dArr;', 'darr;', 'deg;', 'Delta;', 'delta;', 'diams;', 'divide;', 'Eacute;', 'eacute;', 'Ecirc;', 'ecirc;', 'Egrave;', 'egrave;', 'empty;', 'emsp;', 'ensp;', 'Epsilon;', 'epsilon;', 'equiv;', 'Eta;', 'eta;', 'ETH;', 'eth;', 'Euml;', 'euml;', 'euro;', 'exist;', 'fnof;', 'forall;', 'frac12;', 'frac14;', 'frac34;', 'frasl;', 'Gamma;', 'gamma;', 'ge;', 'gt;', 'hArr;', 'harr;', 'hearts;', 'hellip;', 'Iacute;', 'iacute;', 'Icirc;', 'icirc;', 'iexcl;', 'Igrave;', 'igrave;', 'image;', 'infin;', 'int;', 'Iota;', 'iota;', 'iquest;', 'isin;', 'Iuml;', 'iuml;', 'Kappa;', 'kappa;', 'Lambda;', 'lambda;', 'lang;', 'laquo;', 'lArr;', 'larr;', 'lceil;', 'ldquo;', 'le;', 'lfloor;', 'lowast;', 'loz;', 'lrm;', 'lsaquo;', 'lsquo;', 'lt;', 'macr;', 'mdash;', 'micro;', 'middot;', 'minus;', 'Mu;', 'mu;', 'nabla;', 'nbsp;', 'ndash;', 'ne;', 'ni;', 'not;', 'notin;', 'nsub;', 'Ntilde;', 'ntilde;', 'Nu;', 'nu;', 'Oacute;', 'oacute;', 'Ocirc;', 'ocirc;', 'OElig;', 'oelig;', 'Ograve;', 'ograve;', 'oline;', 'Omega;', 'omega;', 'Omicron;', 'omicron;', 'oplus;', 'or;', 'ordf;', 'ordm;', 'Oslash;', 'oslash;', 'Otilde;', 'otilde;', 'otimes;', 'Ouml;', 'ouml;', 'para;', 'part;', 'permil;', 'perp;', 'Phi;', 'phi;', 'Pi;', 'pi;', 'piv;', 'plusmn;', 'pound;', 'Prime;', 'prime;', 'prod;', 'prop;', 'Psi;', 'psi;', 'quot;', 'radic;', 'rang;', 'raquo;', 'rArr;', 'rarr;', 'rceil;', 'rdquo;', 'real;', 'reg;', 'rfloor;', 'Rho;', 'rho;', 'rlm;', 'rsaquo;', 'rsquo;', 'sbquo;', 'Scaron;', 'scaron;', 'sdot;', 'sect;', 'shy;', 'Sigma;', 'sigma;', 'sigmaf;', 'sim;', 'spades;', 'sub;', 'sube;', 'sum;', 'sup;', 'sup1;', 'sup2;', 'sup3;', 'supe;', 'szlig;', 'Tau;', 'tau;', 'there4;', 'Theta;', 'theta;', 'thetasym;', 'thinsp;', 'THORN;', 'thorn;', 'tilde;', 'times;', 'trade;', 'Uacute;', 'uacute;', 'uArr;', 'uarr;', 'Ucirc;', 'ucirc;', 'Ugrave;', 'ugrave;', 'uml;', 'upsih;', 'Upsilon;', 'upsilon;', 'Uuml;', 'uuml;', 'weierp;', 'Xi;', 'xi;', 'Yacute;', 'yacute;', 'yen;', 'Yuml;', 'yuml;', 'Zeta;', 'zeta;', 'zwj;', 'zwnj;'];

        return values.map(function(value){
            return {
                caption: value,
                snippet: value,
                meta: "html entity",
                score: 1000000
            };
        });
    };

}).call(HtmlCompletions.prototype);

exports.HtmlCompletions = HtmlCompletions;
});

define("ace/mode/html",["require","exports","module","ace/lib/oop","ace/lib/lang","ace/mode/text","ace/mode/javascript","ace/mode/css","ace/mode/html_highlight_rules","ace/mode/behaviour/xml","ace/mode/folding/html","ace/mode/html_completions","ace/worker/worker_client"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var lang = require("../lib/lang");
var TextMode = require("./text").Mode;
var JavaScriptMode = require("./javascript").Mode;
var CssMode = require("./css").Mode;
var HtmlHighlightRules = require("./html_highlight_rules").HtmlHighlightRules;
var XmlBehaviour = require("./behaviour/xml").XmlBehaviour;
var HtmlFoldMode = require("./folding/html").FoldMode;
var HtmlCompletions = require("./html_completions").HtmlCompletions;
var WorkerClient = require("../worker/worker_client").WorkerClient;
var voidElements = ["area", "base", "br", "col", "embed", "hr", "img", "input", "keygen", "link", "meta", "menuitem", "param", "source", "track", "wbr"];
var optionalEndTags = ["li", "dt", "dd", "p", "rt", "rp", "optgroup", "option", "colgroup", "td", "th"];

var Mode = function(options) {
    this.fragmentContext = options && options.fragmentContext;
    this.HighlightRules = HtmlHighlightRules;
    this.$behaviour = new XmlBehaviour();
    this.$completer = new HtmlCompletions();
    
    this.createModeDelegates({
        "js-": JavaScriptMode,
        "css-": CssMode
    });
    
    this.foldingRules = new HtmlFoldMode(this.voidElements, lang.arrayToMap(optionalEndTags));
};
oop.inherits(Mode, TextMode);

(function() {

    this.blockComment = {start: "<!--", end: "-->"};

    this.voidElements = lang.arrayToMap(voidElements);

    this.getNextLineIndent = function(state, line, tab) {
        return this.$getIndent(line);
    };

    this.checkOutdent = function(state, line, input) {
        return false;
    };

    this.getCompletions = function(state, session, pos, prefix) {
        return this.$completer.getCompletions(state, session, pos, prefix);
    };

    this.createWorker = function(session) {
        if (this.constructor != Mode)
            return;
        var worker = new WorkerClient(["ace"], "ace/mode/html_worker", "Worker");
        worker.attachToDocument(session.getDocument());

        if (this.fragmentContext)
            worker.call("setOptions", [{context: this.fragmentContext}]);

        worker.on("error", function(e) {
            session.setAnnotations(e.data);
        });

        worker.on("terminate", function() {
            session.clearAnnotations();
        });

        return worker;
    };

    this.$id = "ace/mode/html";
}).call(Mode.prototype);

exports.Mode = Mode;
});

define("ace/mode/markdown_highlight_rules",["require","exports","module","ace/config","ace/lib/oop","ace/lib/lang","ace/mode/text_highlight_rules","ace/mode/html_highlight_rules"], function(require, exports, module) {
"use strict";

var modes = require("../config").$modes;

var oop = require("../lib/oop");
var lang = require("../lib/lang");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var HtmlHighlightRules = require("./html_highlight_rules").HtmlHighlightRules;

var escaped = function(ch) {
    return "(?:[^" + lang.escapeRegExp(ch) + "\\\\]|\\\\.)*";
};

var MarkdownHighlightRules = function() {
    HtmlHighlightRules.call(this);
    var codeBlockStartRule = {
        token : "support.function",
        regex : /^\s*(```+[^`]*|~~~+[^~]*)$/,
        onMatch: function(value, state, stack, line) {
            var m = value.match(/^(\s*)([`~]+)(.*)/);
            var language = /[\w-]+|$/.exec(m[3])[0];
            if (!modes[language])
                language = "";
            stack.unshift("githubblock", [], [m[1], m[2], language], state);
            return this.token;
        },
        next  : "githubblock"
    };
    var codeBlockRules = [{
        token : "support.function",
        regex : ".*",
        onMatch: function(value, state, stack, line) {
            var embedState = stack[1];
            var indent = stack[2][0];
            var endMarker = stack[2][1];
            var language = stack[2][2];
            
            var m = /^(\s*)(`+|~+)\s*$/.exec(value);
            if (
                m && m[1].length < indent.length + 3
                && m[2].length >= endMarker.length && m[2][0] == endMarker[0]
            ) {
                stack.splice(0, 3);
                this.next = stack.shift();
                return this.token;
            }
            this.next = "";
            if (language && modes[language]) {
                var data = modes[language].getTokenizer().getLineTokens(value, embedState.slice(0));
                stack[1] = data.state;
                return data.tokens;
            }
            return this.token;
        }
    }];

    // ---------- [CUSTOM,LISTS] ----------
    let markupListRegex = "^\\s{0,300}(?:[*+\\-%]|\\d+\\.)\\s+";
    let markupListRegexA = "^\\s{0,300}(?:[$])\\s+";
    let markupListRegexB = "^\\s{0,300}(?:[#])\\s+";
    let markupListRegexC = "^\\s{0,300}(?:[@])\\s+";
    let markupListRegexD = "^\\s{0,300}(?:[&])\\s+";
    let markupListRegexE = "^\\s{0,300}(?:[>])\\s+";

    function makeListblock(suffix) {
        return [{
            token: "empty_line",
            regex: "^$",
            next: "start"
        },
        { token: "markup.list", regex: markupListRegex, next: "listblock" },
        { token: "markup.list", regex: markupListRegexA, next: "listblock-a" },
        { token: "markup.list", regex: markupListRegexB, next: "listblock-b" },
        { token: "markup.list", regex: markupListRegexC, next: "listblock-c" },
        { token: "markup.list", regex: markupListRegexD, next: "listblock-d" },
        { token: "markup.list", regex: markupListRegexE, next: "listblock-e" },
        {
            include: "basic", noEscape: true
        },
        codeBlockStartRule,
        {
            defaultToken: suffix ? "list." + suffix : "list"
        }]
    }

    this.$rules["start"].unshift({
        token : "empty_line",
        regex : '^$',
        next: "allowBlock"
    },
    codeBlockStartRule,
    { // block quote
        token : "string.blockquote", // ---------- [CUSTOM,LISTS] ----------
        regex : "^\\s*>>\\s*(?:[*+\\-$#@&%>]|\\d+\\.)?\\s+",
        next  : "blockquote"
    }, { // HR * - _
        token : "constant",
        regex : "^ {0,2}(?:(?: ?\\* ?){3,}|(?: ?\\- ?){3,}|(?: ?\\_ ?){3,})\\s*$",
        next: "allowBlock"
    },

    // ---------- [CUSTOM,LISTS] ----------
    { token: "markup.list", regex: markupListRegex, next: "listblock"},
    { token: "markup.list", regex: markupListRegexA, next: "listblock-a"},
    { token: "markup.list", regex: markupListRegexB, next: "listblock-b"},
    { token: "markup.list", regex: markupListRegexC, next: "listblock-c"},
    { token: "markup.list", regex: markupListRegexD, next: "listblock-d"},
    { token: "markup.list", regex: markupListRegexE, next: "listblock-e"},

    {
        include : "basic"
    });

    this.addRules({
        "basic" : [{
            token : "constant.language.escape",
            regex : /\\[\\`*_{}\[\]()#+\-.!]/
        }, { // code span `
            token : "support.function.inline", // ---------- [CUSTOM] ---------- (inline)
            regex : "(`+)(.*?[^`])(\\1)"
        }, { // reference
            token : ["text", "constant", "text", "url", "string", "text"],
            regex : "^([ ]{0,3}\\[)([^\\]]+)(\\]:\\s*)([^ ]+)(\\s*(?:[\"][^\"]+[\"])?(\\s*))$"
        }, { // link by url // ---------- [CUSTOM] ----------
            token : ["tag.declaration.clickable", "tag.declaration.highlight"],
            regex : "(\\[[A-Z0-9]+\\])" + "(\\([^\\)]+\\))"
        }, { //
            token : ["text", "url", "text"],
            regex : "(<)("+
                      "(?:https?|ftp|dict):[^'\">\\s]+"+
                      "|"+
                      "(?:mailto:)?[-.\\w]+\\@[-a-z0-9]+(?:\\.[-a-z0-9]+)*\\.[a-z]+"+
                    ")(>)"
        }, { // ---------- [CUSTOM] ---------- [TAG-SYNTAX]
            token: "tag.declaration.clickable",
            regex: "\\[[A-Z0-9]+\\]",
            caseInsensitive: false
        }, { // ---------- [CUSTOM] ----------
            token: "tag.definition.clickable",
            regex: "\\[[A-Z0-9]+=\\]",
            caseInsensitive: false
        }, { // ---------- [CUSTOM] ----------
            token: "tag.reference.clickable",
            regex: "\\[[A-Z0-9]*(@[A-Z0-9]*=?|:[0-9]+(,[0-9]+)?(-[0-9]+(,[0-9]+)?)?)\\]",
            caseInsensitive: false
        }, { // ---------- [CUSTOM] ----------
            token: "ntag.definition",
            regex: "\\[&[A-Z0-9]+=\\]",
            caseInsensitive: false
        }, { // ---------- [CUSTOM] ----------
            token: function(text) {
                return "color.literal." + text.substring(1)
            },
            regex: "#[0-9A-F]{6}",
            caseInsensitive: true
        }, { // ---------- [CUSTOM] ----------
            token: "underline.link.clickable", // URL -> http://example.com
            regex: "[^\\s]*\\:\\/\\/[^\\s]+"
        }, { // ---------- [CUSTOM] ----------
            token: "image", // [*614c18b7-1aa5-4dc2-b611-e7a31818c6a1], [-614c18b7-1aa5-4dc2-b611-e7a31818c6a1]
            regex: "\\[[-\\*][^\\]]+\\]"
        }, {
            token: "timestamp",
            regex: "([0-9]{4}-[0-9]{2}-[0-9]{2},? |[0-9]{2}-[0-9]{2},? |)([0-9]{1,2}(:[0-9]{2})?(:[0-9]{2})?( AM| PM|) ?- ?)?([0-9]{1,2}(:[0-9]{2})?(:[0-9]{2})? (AM|PM)|[0-9]{1,2}:[0-9]{2}(:[0-9]{2})?)"
        }, {
            token: "duration",
            regex: "(t|T)\\( *(([0-9]+)?\\.?[0-9]+(d|h|m|s) *)+\\)"
        }, {
            token: "ritsu.amount",
            regex: "<-?([0-9]+)?\\.?[0-9]+>"
        }, {
            token: "ritsu.pass",
            regex: "<P(x[0-9]+)?>"
        }, {
            token: "ritsu.fail",
            regex: "<F((x[0-9]+)|-[0-9]*\\.?[0-9]+)?>"
        }],

        "allowBlock": [
            // ---------- [CUSTOM-RM] ----------
            // {token : "support.function", regex : "^ {4}.+", next : "allowBlock"},
            {token: "empty_line", regex: '^$', next: "allowBlock"},
            {token: "empty", regex: "", next: "start"}
        ],

        // ---------- [CUSTOM] ----------
        // the "listblock" is a continuous section spanning multiple lines
        // only escape once reached an empty block
        "listblock": makeListblock(),
        "listblock-a": makeListblock("a"),
        "listblock-b": makeListblock("b"),
        "listblock-c": makeListblock("c"),
        "listblock-d": makeListblock("d"),
        "listblock-e": makeListblock("e"),

        "blockquote" : [ { // Blockquotes only escape on blank lines.
            token : "empty_line",
            regex : "^\\s*$",
            next  : "start"
        }, { // block quote
            token : "string.blockquote", // ---------- [CUSTOM,LISTS] ----------
            regex : "^\\s*>\\s*(?:[*+\\-$#@&%>]|\\d+\\.)?\\s+",
            next  : "blockquote"
        }, {
            include : "basic", noEscape: true
        }, {
            defaultToken : "string.blockquote"
        } ],

        "githubblock" : codeBlockRules
    });

    this.normalizeRules();
};
oop.inherits(MarkdownHighlightRules, TextHighlightRules);

exports.MarkdownHighlightRules = MarkdownHighlightRules;
});

define("ace/mode/folding/markdown",["require","exports","module","ace/lib/oop","ace/mode/folding/fold_mode","ace/range"], function(require, exports, module) {
"use strict";

var oop = require("../../lib/oop");
var BaseFoldMode = require("./fold_mode").FoldMode;
var Range = require("../../range").Range;

var FoldMode = exports.FoldMode = function() {};
oop.inherits(FoldMode, BaseFoldMode);

(function() {
    this.foldingStartMarker = /^(?:[=-]+\s*$|`{3})/;

    this.getFoldWidget = function(session, foldStyle, row) {
        var line = session.getLine(row);
        if (!this.foldingStartMarker.test(line))
            return "";

        if (line[0] == "`") {
            if (session.bgTokenizer.getState(row) == "start")
                return "end";
            return "start";
        }

        return "start";
    };

    this.getFoldWidgetRange = function(session, foldStyle, row) {
        var line = session.getLine(row);
        var startColumn = line.length;
        var maxRow = session.getLength();
        var startRow = row;
        var endRow = row;
        if (!line.match(this.foldingStartMarker))
            return;

        if (line[0] == "`") {
            if (session.bgTokenizer.getState(row) !== "start") {
                while (++row < maxRow) {
                    line = session.getLine(row);
                    if (line[0] == "`" & line.substring(0, 3) == "```")
                        break;
                }
                return new Range(startRow, startColumn, row, 0);
            } else {
                while (row -- > 0) {
                    line = session.getLine(row);
                    if (line[0] == "`" & line.substring(0, 3) == "```")
                        break;
                }
                return new Range(row, line.length, startRow, 0);
            }
        }

        var token;
        function isHeading(row) {
            token = session.getTokens(row)[0];
            return token && token.type.lastIndexOf(heading, 0) === 0;
        }

        var heading = "markup.heading";
        function getLevel() {
            var ch = token.value[0];
            if (ch == "=") return 6;
            if (ch == "-") return 5;
            return 7 - token.value.search(/[^#]|$/);
        }

        if (isHeading(row)) {
            var startHeadingLevel = getLevel();
            while (++row < maxRow) {
                if (!isHeading(row))
                    continue;
                var level = getLevel();
                if (level >= startHeadingLevel)
                    break;
            }

            endRow = row - (!token || ["=", "-"].indexOf(token.value[0]) == -1 ? 1 : 2);

            if (endRow > startRow) {
                while (endRow > startRow && /^\s*$/.test(session.getLine(endRow)))
                    endRow--;
            }

            if (endRow > startRow) {
                var endColumn = session.getLine(endRow).length;
                return new Range(startRow, startColumn, endRow, endColumn);
            }
        }
    };

}).call(FoldMode.prototype);

});

define("ace/mode/sh_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;

var reservedKeywords = exports.reservedKeywords = (
        '!|{|}|case|do|done|elif|else|'+
        'esac|fi|for|if|in|then|until|while|'+
        '&|;|export|local|read|typeset|unset|'+
        'elif|select|set|function|declare|readonly'
    );

var languageConstructs = exports.languageConstructs = (
    '[|]|alias|bg|bind|break|builtin|'+
     'cd|command|compgen|complete|continue|'+
     'dirs|disown|echo|enable|eval|exec|'+
     'exit|fc|fg|getopts|hash|help|history|'+
     'jobs|kill|let|logout|popd|printf|pushd|'+
     'pwd|return|set|shift|shopt|source|'+
     'suspend|test|times|trap|type|ulimit|'+
     'umask|unalias|wait'
);

var ShHighlightRules = function() {
    var keywordMapper = this.createKeywordMapper({
        "keyword": reservedKeywords,
        "support.function.builtin": languageConstructs,
        "invalid.deprecated": "debugger"
    }, "identifier");

    var integer = "(?:(?:[1-9]\\d*)|(?:0))";

    var fraction = "(?:\\.\\d+)";
    var intPart = "(?:\\d+)";
    var pointFloat = "(?:(?:" + intPart + "?" + fraction + ")|(?:" + intPart + "\\.))";
    var exponentFloat = "(?:(?:" + pointFloat + "|" +  intPart + ")" + ")";
    var floatNumber = "(?:" + exponentFloat + "|" + pointFloat + ")";
    var fileDescriptor = "(?:&" + intPart + ")";

    var variableName = "[a-zA-Z_][a-zA-Z0-9_]*";
    var variable = "(?:" + variableName + "(?==))";

    var builtinVariable = "(?:\\$(?:SHLVL|\\$|\\!|\\?))";

    var func = "(?:" + variableName + "\\s*\\(\\))";

    this.$rules = {
        "start" : [{
            token : "constant",
            regex : /\\./
        }, {
            token : ["text", "comment"],
            regex : /(^|\s)(#.*)$/
        }, {
            token : "string.start",
            regex : '"',
            push : [{
                token : "constant.language.escape",
                regex : /\\(?:[$`"\\]|$)/
            }, {
                include : "variables"
            }, {
                token : "keyword.operator",
                regex : /`/ // TODO highlight `
            }, {
                token : "string.end",
                regex : '"',
                next: "pop"
            }, {
                defaultToken: "string"
            }]
        }, {
            token : "string",
            regex : "\\$'",
            push : [{
                token : "constant.language.escape",
                regex : /\\(?:[abeEfnrtv\\'"]|x[a-fA-F\d]{1,2}|u[a-fA-F\d]{4}([a-fA-F\d]{4})?|c.|\d{1,3})/
            }, {
                token : "string",
                regex : "'",
                next: "pop"
            }, {
                defaultToken: "string"
            }]
        }, {
            regex : "<<<",
            token : "keyword.operator"
        }, {
            stateName: "heredoc",
            regex : "(<<-?)(\\s*)(['\"`]?)([\\w\\-]+)(['\"`]?)",
            onMatch : function(value, currentState, stack) {
                var next = value[2] == '-' ? "indentedHeredoc" : "heredoc";
                var tokens = value.split(this.splitRegex);
                stack.push(next, tokens[4]);
                return [
                    {type:"constant", value: tokens[1]},
                    {type:"text", value: tokens[2]},
                    {type:"string", value: tokens[3]},
                    {type:"support.class", value: tokens[4]},
                    {type:"string", value: tokens[5]}
                ];
            },
            rules: {
                heredoc: [{
                    onMatch:  function(value, currentState, stack) {
                        if (value === stack[1]) {
                            stack.shift();
                            stack.shift();
                            this.next = stack[0] || "start";
                            return "support.class";
                        }
                        this.next = "";
                        return "string";
                    },
                    regex: ".*$",
                    next: "start"
                }],
                indentedHeredoc: [{
                    token: "string",
                    regex: "^\t+"
                }, {
                    onMatch:  function(value, currentState, stack) {
                        if (value === stack[1]) {
                            stack.shift();
                            stack.shift();
                            this.next = stack[0] || "start";
                            return "support.class";
                        }
                        this.next = "";
                        return "string";
                    },
                    regex: ".*$",
                    next: "start"
                }]
            }
        }, {
            regex : "$",
            token : "empty",
            next : function(currentState, stack) {
                if (stack[0] === "heredoc" || stack[0] === "indentedHeredoc")
                    return stack[0];
                return currentState;
            }
        }, {
            token : ["keyword", "text", "text", "text", "variable"],
            regex : /(declare|local|readonly)(\s+)(?:(-[fixar]+)(\s+))?([a-zA-Z_][a-zA-Z0-9_]*\b)/
        }, {
            token : "variable.language",
            regex : builtinVariable
        }, {
            token : "variable",
            regex : variable
        }, {
            include : "variables"
        }, {
            token : "support.function",
            regex : func
        }, {
            token : "support.function",
            regex : fileDescriptor
        }, {
            token : "string",           // ' string
            start : "'", end : "'"
        }, {
            token : "constant.numeric", // float
            regex : floatNumber
        }, {
            token : "constant.numeric", // integer
            regex : integer + "\\b"
        }, {
            token : keywordMapper,
            regex : "[a-zA-Z_][a-zA-Z0-9_]*\\b"
        }, {
            token : "keyword.operator",
            regex : "\\+|\\-|\\*|\\*\\*|\\/|\\/\\/|~|<|>|<=|=>|=|!=|[%&|`]"
        }, {
            token : "punctuation.operator",
            regex : ";"
        }, {
            token : "paren.lparen",
            regex : "[\\[\\(\\{]"
        }, {
            token : "paren.rparen",
            regex : "[\\]]"
        }, {
            token : "paren.rparen",
            regex : "[\\)\\}]",
            next : "pop"
        }],
        variables: [{
            token : "variable",
            regex : /(\$)(\w+)/
        }, {
            token : ["variable", "paren.lparen"],
            regex : /(\$)(\()/,
            push : "start"
        }, {
            token : ["variable", "paren.lparen", "keyword.operator", "variable", "keyword.operator"],
            regex : /(\$)(\{)([#!]?)(\w+|[*@#?\-$!0_])(:[?+\-=]?|##?|%%?|,,?\/|\^\^?)?/,
            push : "start"
        }, {
            token : "variable",
            regex : /\$[*@#?\-$!0_]/
        }, {
            token : ["variable", "paren.lparen"],
            regex : /(\$)(\{)/,
            push : "start"
        }]
    };
    
    this.normalizeRules();
};

oop.inherits(ShHighlightRules, TextHighlightRules);

exports.ShHighlightRules = ShHighlightRules;
});

define("ace/mode/sh",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/sh_highlight_rules","ace/range","ace/mode/folding/cstyle","ace/mode/behaviour/cstyle"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var ShHighlightRules = require("./sh_highlight_rules").ShHighlightRules;
var Range = require("../range").Range;
var CStyleFoldMode = require("./folding/cstyle").FoldMode;
var CstyleBehaviour = require("./behaviour/cstyle").CstyleBehaviour;

var Mode = function() {
    this.HighlightRules = ShHighlightRules;
    this.foldingRules = new CStyleFoldMode();
    this.$behaviour = new CstyleBehaviour();
};
oop.inherits(Mode, TextMode);

(function() {

   
    this.lineCommentStart = "#";

    this.getNextLineIndent = function(state, line, tab) {
        var indent = this.$getIndent(line);

        var tokenizedLine = this.getTokenizer().getLineTokens(line, state);
        var tokens = tokenizedLine.tokens;

        if (tokens.length && tokens[tokens.length-1].type == "comment") {
            return indent;
        }

        if (state == "start") {
            var match = line.match(/^.*[\{\(\[:]\s*$/);
            if (match) {
                indent += tab;
            }
        }

        return indent;
    };

    var outdents = {
        "pass": 1,
        "return": 1,
        "raise": 1,
        "break": 1,
        "continue": 1
    };

    this.checkOutdent = function(state, line, input) {
        if (input !== "\r\n" && input !== "\r" && input !== "\n")
            return false;

        var tokens = this.getTokenizer().getLineTokens(line.trim(), state).tokens;

        if (!tokens)
            return false;
        do {
            var last = tokens.pop();
        } while (last && (last.type == "comment" || (last.type == "text" && last.value.match(/^\s+$/))));

        if (!last)
            return false;

        return (last.type == "keyword" && outdents[last.value]);
    };

    this.autoOutdent = function(state, doc, row) {

        row += 1;
        var indent = this.$getIndent(doc.getLine(row));
        var tab = doc.getTabString();
        if (indent.slice(-tab.length) == tab)
            doc.remove(new Range(row, indent.length-tab.length, row, indent.length));
    };

    this.$id = "ace/mode/sh";
}).call(Mode.prototype);

exports.Mode = Mode;
});

// ------------------------------------------------------- [BEGIN CLOJURE SECTION] -------------------------------------------------------

define("ace/mode/clojure_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;



    var ClojureHighlightRules = function() {

        var builtinFunctions = (
            '* *1 *2 *3 *agent* *allow-unresolved-vars* *assert* *clojure-version* ' +
            '*command-line-args* *compile-files* *compile-path* *e *err* *file* ' +
            '*flush-on-newline* *in* *macro-meta* *math-context* *ns* *out* ' +
            '*print-dup* *print-length* *print-level* *print-meta* *print-readably* ' +
            '*read-eval* *source-path* *use-context-classloader* ' +
            '*warn-on-reflection* + - -> ->> .. / < <= = ' +
            '== > &gt; >= &gt;= accessor aclone ' +
            'add-classpath add-watch agent agent-errors aget alength alias all-ns ' +
            'alter alter-meta! alter-var-root amap ancestors and apply areduce ' +
            'array-map aset aset-boolean aset-byte aset-char aset-double aset-float ' +
            'aset-int aset-long aset-short assert assoc assoc! assoc-in associative? ' +
            'atom await await-for await1 bases bean bigdec bigint binding bit-and ' +
            'bit-and-not bit-clear bit-flip bit-not bit-or bit-set bit-shift-left ' +
            'bit-shift-right bit-test bit-xor boolean boolean-array booleans ' +
            'bound-fn bound-fn* butlast byte byte-array bytes cast char char-array ' +
            'char-escape-string char-name-string char? chars chunk chunk-append ' +
            'chunk-buffer chunk-cons chunk-first chunk-next chunk-rest chunked-seq? ' +
            'class class? clear-agent-errors clojure-version coll? comment commute ' +
            'comp comparator compare compare-and-set! compile complement concat cond ' +
            'condp conj conj! cons constantly construct-proxy contains? count ' +
            'counted? create-ns create-struct cycle dec decimal? declare definline ' +
            'defmacro defmethod defmulti defn defn- defonce defstruct delay delay? ' +
            'deliver deref derive descendants destructure disj disj! dissoc dissoc! ' +
            'distinct distinct? doall doc dorun doseq dosync dotimes doto double ' +
            'double-array doubles drop drop-last drop-while empty empty? ensure ' +
            'enumeration-seq eval even? every? false? ffirst file-seq filter find ' +
            'find-doc find-ns find-var first float float-array float? floats flush ' +
            'fn fn? fnext for force format future future-call future-cancel ' +
            'future-cancelled? future-done? future? gen-class gen-interface gensym ' +
            'get get-in get-method get-proxy-class get-thread-bindings get-validator ' +
            'hash hash-map hash-set identical? identity if-let if-not ifn? import ' +
            'in-ns inc init-proxy instance? int int-array integer? interleave intern ' +
            'interpose into into-array ints io! isa? iterate iterator-seq juxt key ' +
            'keys keyword keyword? last lazy-cat lazy-seq let letfn line-seq list ' +
            'list* list? load load-file load-reader load-string loaded-libs locking ' +
            'long long-array longs loop macroexpand macroexpand-1 make-array ' +
            'make-hierarchy map map? mapcat max max-key memfn memoize merge ' +
            'merge-with meta method-sig methods min min-key mod name namespace neg? ' +
            'newline next nfirst nil? nnext not not-any? not-empty not-every? not= ' +
            'ns ns-aliases ns-imports ns-interns ns-map ns-name ns-publics ' +
            'ns-refers ns-resolve ns-unalias ns-unmap nth nthnext num number? odd? ' +
            'or parents partial partition pcalls peek persistent! pmap pop pop! ' +
            'pop-thread-bindings pos? pr pr-str prefer-method prefers ' +
            'primitives-classnames print print-ctor print-doc print-dup print-method ' +
            'print-namespace-doc print-simple print-special-doc print-str printf ' +
            'println println-str prn prn-str promise proxy proxy-call-with-super ' +
            'proxy-mappings proxy-name proxy-super push-thread-bindings pvalues quot ' +
            'rand rand-int range ratio? rational? rationalize re-find re-groups ' +
            're-matcher re-matches re-pattern re-seq read read-line read-string ' +
            'reduce ref ref-history-count ref-max-history ref-min-history ref-set ' +
            'refer refer-clojure release-pending-sends rem remove remove-method ' +
            'remove-ns remove-watch repeat repeatedly replace replicate require ' +
            'reset! reset-meta! resolve rest resultset-seq reverse reversible? rseq ' +
            'rsubseq second select-keys send send-off seq seq? seque sequence ' +
            'sequential? set set-validator! set? short short-array shorts ' +
            'shutdown-agents slurp some sort sort-by sorted-map sorted-map-by ' +
            'sorted-set sorted-set-by sorted? special-form-anchor special-symbol? ' +
            'split-at split-with str stream? string? struct struct-map subs subseq ' +
            'subvec supers swap! symbol symbol? sync syntax-symbol-anchor take ' +
            'take-last take-nth take-while test the-ns time to-array to-array-2d ' +
            'trampoline transient tree-seq true? type unchecked-add unchecked-dec ' +
            'unchecked-divide unchecked-inc unchecked-multiply unchecked-negate ' +
            'unchecked-remainder unchecked-subtract underive unquote ' +
            'unquote-splicing update-in update-proxy use val vals var-get var-set ' +
            'var? vary-meta vec vector vector? when when-first when-let when-not ' +
            'while with-bindings with-bindings* with-in-str with-loading-context ' +
            'with-local-vars with-meta with-open with-out-str with-precision xml-seq ' +
            'zero? zipmap'
        );

        var keywords = ('throw try var ' +
            'def do fn if let loop monitor-enter monitor-exit new quote recur set!'
        );

        var buildinConstants = ("true false nil");

        var keywordMapper = this.createKeywordMapper({
            "keyword": keywords,
            "constant.language": buildinConstants,
            "support.function": builtinFunctions
        }, "identifier", false, " ");

        this.$rules = {
            "start" : [
                {
                    token : "comment",
                    regex : ";.*$"
                }, {
                    token : "keyword", //parens
                    regex : "[\\(|\\)]"
                }, {
                    token : "keyword", //lists
                    regex : "[\\'\\(]"
                }, {
                    token : "keyword", //vectors
                    regex : "[\\[|\\]]"
                }, {
                    token : "keyword", //sets and maps
                    regex : "[\\{|\\}|\\#\\{|\\#\\}]"
                }, {
                    token : "keyword", // ampersands
                    regex : '[\\&]'
                }, {
                    token : "keyword", // metadata
                    regex : '[\\#\\^\\{]'
                }, {
                    token : "keyword", // anonymous fn syntactic sugar
                    regex : '[\\%]'
                }, {
                    token : "keyword", // deref reader macro
                    regex : '[@]'
                }, {
                    token : "constant.numeric", // hex
                    regex : "0[xX][0-9a-fA-F]+\\b"
                }, {
                    token : "constant.numeric", // float
                    regex : "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"
                }, {
                    token : "constant.language",
                    regex : '[!|\\$|%|&|\\*|\\-\\-|\\-|\\+\\+|\\+||=|!=|<=|>=|<>|<|>|!|&&]'
                }, {
                    token : keywordMapper,
                    regex : "[a-zA-Z_$][a-zA-Z0-9_$\\-]*\\b"
                }, {
                    token : "string", // single line
                    regex : '"',
                    next: "string"
                }, {
                    token : "constant", // symbol
                    regex : /:[^()\[\]{}'"\^%`,;\s]+/
                }, {
                    token : "string.regexp", //Regular Expressions
                    regex : '/#"(?:\\.|(?:\\")|[^""\n])*"/g'
                }

            ],
            "string" : [
                {
                    token : "constant.language.escape",
                    regex : "\\\\.|\\\\$"
                }, {
                    token : "string",
                    regex : '[^"\\\\]+'
                }, {
                    token : "string",
                    regex : '"',
                    next : "start"
                }
            ]
        };
    };

    oop.inherits(ClojureHighlightRules, TextHighlightRules);

    exports.ClojureHighlightRules = ClojureHighlightRules;
});

define("ace/mode/matching_parens_outdent",["require","exports","module","ace/range"], function(require, exports, module) {
    "use strict";

    var Range = require("../range").Range;

    var MatchingParensOutdent = function() {};

    (function() {

        this.checkOutdent = function(line, input) {
            if (! /^\s+$/.test(line))
                return false;

            return /^\s*\)/.test(input);
        };

        this.autoOutdent = function(doc, row) {
            var line = doc.getLine(row);
            var match = line.match(/^(\s*\))/);

            if (!match) return 0;

            var column = match[1].length;
            var openBracePos = doc.findMatchingBracket({row: row, column: column});

            if (!openBracePos || openBracePos.row == row) return 0;

            var indent = this.$getIndent(doc.getLine(openBracePos.row));
            doc.replace(new Range(row, 0, row, column-1), indent);
        };

        this.$getIndent = function(line) {
            var match = line.match(/^(\s+)/);
            if (match) {
                return match[1];
            }

            return "";
        };

    }).call(MatchingParensOutdent.prototype);

    exports.MatchingParensOutdent = MatchingParensOutdent;
});

define("ace/mode/clojure",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/clojure_highlight_rules","ace/mode/matching_parens_outdent"], function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var TextMode = require("./text").Mode;
    var ClojureHighlightRules = require("./clojure_highlight_rules").ClojureHighlightRules;
    var MatchingParensOutdent = require("./matching_parens_outdent").MatchingParensOutdent;

    var Mode = function() {
        this.HighlightRules = ClojureHighlightRules;
        this.$outdent = new MatchingParensOutdent();
        this.$behaviour = this.$defaultBehaviour;
    };
    oop.inherits(Mode, TextMode);

    (function() {

        this.lineCommentStart = ";";
        this.minorIndentFunctions = ["defn", "defn-", "defmacro", "def", "deftest", "testing"];

        this.$toIndent = function(str) {
            return str.split('').map(function(ch) {
                if (/\s/.exec(ch)) {
                    return ch;
                } else {
                    return ' ';
                }
            }).join('');
        };

        this.$calculateIndent = function(line, tab) {
            var baseIndent = this.$getIndent(line);
            var delta = 0;
            var isParen, ch;
            for (var i = line.length - 1; i >= 0; i--) {
                ch = line[i];
                if (ch === '(') {
                    delta--;
                    isParen = true;
                } else if (ch === '(' || ch === '[' || ch === '{') {
                    delta--;
                    isParen = false;
                } else if (ch === ')' || ch === ']' || ch === '}') {
                    delta++;
                }
                if (delta < 0) {
                    break;
                }
            }
            if (delta < 0 && isParen) {
                i += 1;
                var iBefore = i;
                var fn = '';
                while (true) {
                    ch = line[i];
                    if (ch === ' ' || ch === '\t') {
                        if(this.minorIndentFunctions.indexOf(fn) !== -1) {
                            return this.$toIndent(line.substring(0, iBefore - 1) + tab);
                        } else {
                            return this.$toIndent(line.substring(0, i + 1));
                        }
                    } else if (ch === undefined) {
                        return this.$toIndent(line.substring(0, iBefore - 1) + tab);
                    }
                    fn += line[i];
                    i++;
                }
            } else if(delta < 0 && !isParen) {
                return this.$toIndent(line.substring(0, i+1));
            } else if(delta > 0) {
                baseIndent = baseIndent.substring(0, baseIndent.length - tab.length);
                return baseIndent;
            } else {
                return baseIndent;
            }
        };

        this.getNextLineIndent = function(state, line, tab) {
            return this.$calculateIndent(line, tab);
        };

        this.checkOutdent = function(state, line, input) {
            return this.$outdent.checkOutdent(line, input);
        };

        this.autoOutdent = function(state, doc, row) {
            this.$outdent.autoOutdent(doc, row);
        };

        this.$id = "ace/mode/clojure";
    }).call(Mode.prototype);

    exports.Mode = Mode;
});                (function() {
    window.require(["ace/mode/clojure"], function(m) {
        if (typeof module == "object" && typeof exports == "object" && module) {
            module.exports = m;
        }
    });
})();

// ------------------------------------------------------- [BEGIN C/C++ SECTION] -------------------------------------------------------

define("ace/mode/c_cpp_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/doc_comment_highlight_rules","ace/mode/text_highlight_rules"], function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var DocCommentHighlightRules = require("./doc_comment_highlight_rules").DocCommentHighlightRules;
    var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
    var cFunctions = exports.cFunctions = "\\b(?:hypot(?:f|l)?|s(?:scanf|ystem|nprintf|ca(?:nf|lb(?:n(?:f|l)?|ln(?:f|l)?))|i(?:n(?:h(?:f|l)?|f|l)?|gn(?:al|bit))|tr(?:s(?:tr|pn)|nc(?:py|at|mp)|c(?:spn|hr|oll|py|at|mp)|to(?:imax|d|u(?:l(?:l)?|max)|k|f|l(?:d|l)?)|error|pbrk|ftime|len|rchr|xfrm)|printf|et(?:jmp|vbuf|locale|buf)|qrt(?:f|l)?|w(?:scanf|printf)|rand)|n(?:e(?:arbyint(?:f|l)?|xt(?:toward(?:f|l)?|after(?:f|l)?))|an(?:f|l)?)|c(?:s(?:in(?:h(?:f|l)?|f|l)?|qrt(?:f|l)?)|cos(?:h(?:f)?|f|l)?|imag(?:f|l)?|t(?:ime|an(?:h(?:f|l)?|f|l)?)|o(?:s(?:h(?:f|l)?|f|l)?|nj(?:f|l)?|pysign(?:f|l)?)|p(?:ow(?:f|l)?|roj(?:f|l)?)|e(?:il(?:f|l)?|xp(?:f|l)?)|l(?:o(?:ck|g(?:f|l)?)|earerr)|a(?:sin(?:h(?:f|l)?|f|l)?|cos(?:h(?:f|l)?|f|l)?|tan(?:h(?:f|l)?|f|l)?|lloc|rg(?:f|l)?|bs(?:f|l)?)|real(?:f|l)?|brt(?:f|l)?)|t(?:ime|o(?:upper|lower)|an(?:h(?:f|l)?|f|l)?|runc(?:f|l)?|gamma(?:f|l)?|mp(?:nam|file))|i(?:s(?:space|n(?:ormal|an)|cntrl|inf|digit|u(?:nordered|pper)|p(?:unct|rint)|finite|w(?:space|c(?:ntrl|type)|digit|upper|p(?:unct|rint)|lower|al(?:num|pha)|graph|xdigit|blank)|l(?:ower|ess(?:equal|greater)?)|al(?:num|pha)|gr(?:eater(?:equal)?|aph)|xdigit|blank)|logb(?:f|l)?|max(?:div|abs))|di(?:v|fftime)|_Exit|unget(?:c|wc)|p(?:ow(?:f|l)?|ut(?:s|c(?:har)?|wc(?:har)?)|error|rintf)|e(?:rf(?:c(?:f|l)?|f|l)?|x(?:it|p(?:2(?:f|l)?|f|l|m1(?:f|l)?)?))|v(?:s(?:scanf|nprintf|canf|printf|w(?:scanf|printf))|printf|f(?:scanf|printf|w(?:scanf|printf))|w(?:scanf|printf)|a_(?:start|copy|end|arg))|qsort|f(?:s(?:canf|e(?:tpos|ek))|close|tell|open|dim(?:f|l)?|p(?:classify|ut(?:s|c|w(?:s|c))|rintf)|e(?:holdexcept|set(?:e(?:nv|xceptflag)|round)|clearexcept|testexcept|of|updateenv|r(?:aiseexcept|ror)|get(?:e(?:nv|xceptflag)|round))|flush|w(?:scanf|ide|printf|rite)|loor(?:f|l)?|abs(?:f|l)?|get(?:s|c|pos|w(?:s|c))|re(?:open|e|ad|xp(?:f|l)?)|m(?:in(?:f|l)?|od(?:f|l)?|a(?:f|l|x(?:f|l)?)?))|l(?:d(?:iv|exp(?:f|l)?)|o(?:ngjmp|cal(?:time|econv)|g(?:1(?:p(?:f|l)?|0(?:f|l)?)|2(?:f|l)?|f|l|b(?:f|l)?)?)|abs|l(?:div|abs|r(?:int(?:f|l)?|ound(?:f|l)?))|r(?:int(?:f|l)?|ound(?:f|l)?)|gamma(?:f|l)?)|w(?:scanf|c(?:s(?:s(?:tr|pn)|nc(?:py|at|mp)|c(?:spn|hr|oll|py|at|mp)|to(?:imax|d|u(?:l(?:l)?|max)|k|f|l(?:d|l)?|mbs)|pbrk|ftime|len|r(?:chr|tombs)|xfrm)|to(?:b|mb)|rtomb)|printf|mem(?:set|c(?:hr|py|mp)|move))|a(?:s(?:sert|ctime|in(?:h(?:f|l)?|f|l)?)|cos(?:h(?:f|l)?|f|l)?|t(?:o(?:i|f|l(?:l)?)|exit|an(?:h(?:f|l)?|2(?:f|l)?|f|l)?)|b(?:s|ort))|g(?:et(?:s|c(?:har)?|env|wc(?:har)?)|mtime)|r(?:int(?:f|l)?|ound(?:f|l)?|e(?:name|alloc|wind|m(?:ove|quo(?:f|l)?|ainder(?:f|l)?))|a(?:nd|ise))|b(?:search|towc)|m(?:odf(?:f|l)?|em(?:set|c(?:hr|py|mp)|move)|ktime|alloc|b(?:s(?:init|towcs|rtowcs)|towc|len|r(?:towc|len))))\\b";

    var c_cppHighlightRules = function() {

        var keywordControls = (
            "break|case|continue|default|do|else|for|goto|if|_Pragma|" +
            "return|switch|while|catch|operator|try|throw|using"
        );

        var storageType = (
            "asm|__asm__|auto|bool|_Bool|char|_Complex|double|enum|float|" +
            "_Imaginary|int|long|short|signed|struct|typedef|union|unsigned|void|" +
            "class|wchar_t|template|char16_t|char32_t"
        );

        var storageModifiers = (
            "const|extern|register|restrict|static|volatile|inline|private|" +
            "protected|public|friend|explicit|virtual|export|mutable|typename|" +
            "constexpr|new|delete|alignas|alignof|decltype|noexcept|thread_local"
        );

        var keywordOperators = (
            "and|and_eq|bitand|bitor|compl|not|not_eq|or|or_eq|typeid|xor|xor_eq|" +
            "const_cast|dynamic_cast|reinterpret_cast|static_cast|sizeof|namespace"
        );

        var builtinConstants = (
            "NULL|true|false|TRUE|FALSE|nullptr"
        );

        var keywordMapper = this.$keywords = this.createKeywordMapper({
            "keyword.control" : keywordControls,
            "storage.type" : storageType,
            "storage.modifier" : storageModifiers,
            "keyword.operator" : keywordOperators,
            "variable.language": "this",
            "constant.language": builtinConstants
        }, "identifier");

        var identifierRe = "[a-zA-Z\\$_\u00a1-\uffff][a-zA-Z\\d\\$_\u00a1-\uffff]*\\b";
        var escapeRe = /\\(?:['"?\\abfnrtv]|[0-7]{1,3}|x[a-fA-F\d]{2}|u[a-fA-F\d]{4}U[a-fA-F\d]{8}|.)/.source;
        var formatRe = "%"
            + /(\d+\$)?/.source // field (argument #)
            + /[#0\- +']*/.source // flags
            + /[,;:_]?/.source // separator character (AltiVec)
            + /((-?\d+)|\*(-?\d+\$)?)?/.source // minimum field width
            + /(\.((-?\d+)|\*(-?\d+\$)?)?)?/.source // precision
            + /(hh|h|ll|l|j|t|z|q|L|vh|vl|v|hv|hl)?/.source // length modifier
            + /(\[[^"\]]+\]|[diouxXDOUeEfFgGaACcSspn%])/.source; // conversion type

        this.$rules = {
            "start" : [
                {
                    token : "comment",
                    regex : "//$",
                    next : "start"
                }, {
                    token : "comment",
                    regex : "//",
                    next : "singleLineComment"
                },
                DocCommentHighlightRules.getStartRule("doc-start"),
                {
                    token : "comment", // multi line comment
                    regex : "\\/\\*",
                    next : "comment"
                }, {
                    token : "string", // character
                    regex : "'(?:" + escapeRe + "|.)?'"
                }, {
                    token : "string.start",
                    regex : '"',
                    stateName: "qqstring",
                    next: [
                        { token: "string", regex: /\\\s*$/, next: "qqstring" },
                        { token: "constant.language.escape", regex: escapeRe },
                        { token: "constant.language.escape", regex: formatRe },
                        { token: "string.end", regex: '"|$', next: "start" },
                        { defaultToken: "string"}
                    ]
                }, {
                    token : "string.start",
                    regex : 'R"\\(',
                    stateName: "rawString",
                    next: [
                        { token: "string.end", regex: '\\)"', next: "start" },
                        { defaultToken: "string"}
                    ]
                }, {
                    token : "constant.numeric", // hex
                    regex : "0[xX][0-9a-fA-F]+(L|l|UL|ul|u|U|F|f|ll|LL|ull|ULL)?\\b"
                }, {
                    token : "constant.numeric", // float
                    regex : "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?(L|l|UL|ul|u|U|F|f|ll|LL|ull|ULL)?\\b"
                }, {
                    token : "keyword", // pre-compiler directives
                    regex : "#\\s*(?:include|import|pragma|line|define|undef)\\b",
                    next  : "directive"
                }, {
                    token : "keyword", // special case pre-compiler directive
                    regex : "#\\s*(?:endif|if|ifdef|else|elif|ifndef)\\b"
                }, {
                    token : "support.function.C99.c",
                    regex : cFunctions
                }, {
                    token : keywordMapper,
                    regex : "[a-zA-Z_$][a-zA-Z0-9_$]*"
                }, {
                    token : "keyword.operator",
                    regex : /--|\+\+|<<=|>>=|>>>=|<>|&&|\|\||\?:|[*%\/+\-&\^|~!<>=]=?/
                }, {
                    token : "punctuation.operator",
                    regex : "\\?|\\:|\\,|\\;|\\."
                }, {
                    token : "paren.lparen",
                    regex : "[[({]"
                }, {
                    token : "paren.rparen",
                    regex : "[\\])}]"
                }, {
                    token : "text",
                    regex : "\\s+"
                }
            ],
            "comment" : [
                {
                    token : "comment", // closing comment
                    regex : "\\*\\/",
                    next : "start"
                }, {
                    defaultToken : "comment"
                }
            ],
            "singleLineComment" : [
                {
                    token : "comment",
                    regex : /\\$/,
                    next : "singleLineComment"
                }, {
                    token : "comment",
                    regex : /$/,
                    next : "start"
                }, {
                    defaultToken: "comment"
                }
            ],
            "directive" : [
                {
                    token : "constant.other.multiline",
                    regex : /\\/
                },
                {
                    token : "constant.other.multiline",
                    regex : /.*\\/
                },
                {
                    token : "constant.other",
                    regex : "\\s*<.+?>",
                    next : "start"
                },
                {
                    token : "constant.other", // single line
                    regex : '\\s*["](?:(?:\\\\.)|(?:[^"\\\\]))*?["]',
                    next : "start"
                },
                {
                    token : "constant.other", // single line
                    regex : "\\s*['](?:(?:\\\\.)|(?:[^'\\\\]))*?[']",
                    next : "start"
                },
                {
                    token : "constant.other",
                    regex : /[^\\\/]+/,
                    next : "start"
                }
            ]
        };

        this.embedRules(DocCommentHighlightRules, "doc-",
            [ DocCommentHighlightRules.getEndRule("start") ]);
        this.normalizeRules();
    };

    oop.inherits(c_cppHighlightRules, TextHighlightRules);

    exports.c_cppHighlightRules = c_cppHighlightRules;
});

define("ace/mode/c_cpp",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/c_cpp_highlight_rules","ace/mode/matching_brace_outdent","ace/range","ace/mode/behaviour/cstyle","ace/mode/folding/cstyle"], function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var TextMode = require("./text").Mode;
    var c_cppHighlightRules = require("./c_cpp_highlight_rules").c_cppHighlightRules;
    var MatchingBraceOutdent = require("./matching_brace_outdent").MatchingBraceOutdent;
    var Range = require("../range").Range;
    var CstyleBehaviour = require("./behaviour/cstyle").CstyleBehaviour;
    var CStyleFoldMode = require("./folding/cstyle").FoldMode;

    var Mode = function() {
        this.HighlightRules = c_cppHighlightRules;

        this.$outdent = new MatchingBraceOutdent();
        this.$behaviour = new CstyleBehaviour();

        this.foldingRules = new CStyleFoldMode();
    };
    oop.inherits(Mode, TextMode);

    (function() {

        this.lineCommentStart = "//";
        this.blockComment = {start: "/*", end: "*/"};

        this.getNextLineIndent = function(state, line, tab) {
            var indent = this.$getIndent(line);

            var tokenizedLine = this.getTokenizer().getLineTokens(line, state);
            var tokens = tokenizedLine.tokens;
            var endState = tokenizedLine.state;

            if (tokens.length && tokens[tokens.length-1].type == "comment") {
                return indent;
            }

            if (state == "start") {
                var match = line.match(/^.*[\{\(\[]\s*$/);
                if (match) {
                    indent += tab;
                }
            } else if (state == "doc-start") {
                if (endState == "start") {
                    return "";
                }
                var match = line.match(/^\s*(\/?)\*/);
                if (match) {
                    if (match[1]) {
                        indent += " ";
                    }
                    indent += "* ";
                }
            }

            return indent;
        };

        this.checkOutdent = function(state, line, input) {
            return this.$outdent.checkOutdent(line, input);
        };

        this.autoOutdent = function(state, doc, row) {
            this.$outdent.autoOutdent(doc, row);
        };

        this.$id = "ace/mode/c_cpp";
    }).call(Mode.prototype);

    exports.Mode = Mode;
});                (function() {
    window.require(["ace/mode/c_cpp"], function(m) {
        if (typeof module == "object" && typeof exports == "object" && module) {
            module.exports = m;
        }
    });
})();

// ------------------------------------------------------- [BEGIN SCALA SECTION] -------------------------------------------------------

define("ace/mode/scala_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/doc_comment_highlight_rules","ace/mode/text_highlight_rules"], function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var DocCommentHighlightRules = require("./doc_comment_highlight_rules").DocCommentHighlightRules;
    var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;

    var ScalaHighlightRules = function() {

        var keywords = (
            "case|default|do|else|for|if|match|while|throw|return|try|trye|catch|finally|yield|" +
            "abstract|class|def|extends|final|forSome|implicit|implicits|import|lazy|new|object|null|" +
            "override|package|private|protected|sealed|super|this|trait|type|val|var|with|" +
            "assert|assume|require|print|println|printf|readLine|readBoolean|readByte|readShort|" + // package scala
            "readChar|readInt|readLong|readFloat|readDouble" // package scala
        );

        var buildinConstants = ("true|false");

        var langClasses = (
            "AbstractMethodError|AssertionError|ClassCircularityError|"+
            "ClassFormatError|Deprecated|EnumConstantNotPresentException|"+
            "ExceptionInInitializerError|IllegalAccessError|"+
            "IllegalThreadStateException|InstantiationError|InternalError|"+

            "NegativeArraySizeException|NoSuchFieldError|Override|Process|"+
            "ProcessBuilder|SecurityManager|StringIndexOutOfBoundsException|"+
            "SuppressWarnings|TypeNotPresentException|UnknownError|"+
            "UnsatisfiedLinkError|UnsupportedClassVersionError|VerifyError|"+
            "InstantiationException|IndexOutOfBoundsException|"+
            "ArrayIndexOutOfBoundsException|CloneNotSupportedException|"+
            "NoSuchFieldException|IllegalArgumentException|NumberFormatException|"+
            "SecurityException|Void|InheritableThreadLocal|IllegalStateException|"+
            "InterruptedException|NoSuchMethodException|IllegalAccessException|"+
            "UnsupportedOperationException|Enum|StrictMath|Package|Compiler|"+
            "Readable|Runtime|StringBuilder|Math|IncompatibleClassChangeError|"+
            "NoSuchMethodError|ThreadLocal|RuntimePermission|ArithmeticException|"+
            "NullPointerException|Long|Integer|Short|Byte|Double|Number|Float|"+
            "Character|Boolean|StackTraceElement|Appendable|StringBuffer|"+
            "Iterable|ThreadGroup|Runnable|Thread|IllegalMonitorStateException|"+
            "StackOverflowError|OutOfMemoryError|VirtualMachineError|"+
            "ArrayStoreException|ClassCastException|LinkageError|"+
            "NoClassDefFoundError|ClassNotFoundException|RuntimeException|"+
            "Exception|ThreadDeath|Error|Throwable|System|ClassLoader|"+
            "Cloneable|Class|CharSequence|Comparable|String|Object|" +
            "Unit|Any|AnyVal|AnyRef|Null|ScalaObject|Singleton|Seq|Iterable|List|" +
            "Option|Array|Char|Byte|Int|Long|Nothing|" +

            "App|Application|BufferedIterator|BigDecimal|BigInt|Console|Either|" +
            "Enumeration|Equiv|Fractional|Function|IndexedSeq|Integral|Iterator|" +
            "Map|Numeric|Nil|NotNull|Ordered|Ordering|PartialFunction|PartialOrdering|" +
            "Product|Proxy|Range|Responder|Seq|Serializable|Set|Specializable|Stream|" +
            "StringContext|Symbol|Traversable|TraversableOnce|Tuple|Vector|Pair|Triple"


        );

        var keywordMapper = this.createKeywordMapper({
            "variable.language": "this",
            "keyword": keywords,
            "support.function": langClasses,
            "constant.language": buildinConstants
        }, "identifier");

        this.$rules = {
            "start" : [
                {
                    token : "comment",
                    regex : "\\/\\/.*$"
                },
                DocCommentHighlightRules.getStartRule("doc-start"),
                {
                    token : "comment", // multi line comment
                    regex : "\\/\\*",
                    next : "comment"
                }, {
                    token : "string.regexp",
                    regex : "[/](?:(?:\\[(?:\\\\]|[^\\]])+\\])|(?:\\\\/|[^\\]/]))*[/]\\w*\\s*(?=[).,;]|$)"
                }, {
                    token : "string",
                    regex : '"""',
                    next : "tstring"
                }, {
                    token : "string",
                    regex : '"(?=.)', // " strings can't span multiple lines
                    next : "string"
                }, {
                    token : "symbol.constant", // single line
                    regex : "'[\\w\\d_]+"
                }, {
                    token : "constant.numeric", // hex
                    regex : "0[xX][0-9a-fA-F]+\\b"
                }, {
                    token : "constant.numeric", // float
                    regex : "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"
                }, {
                    token : "constant.language.boolean",
                    regex : "(?:true|false)\\b"
                }, {
                    token : keywordMapper,
                    regex : "[a-zA-Z_$][a-zA-Z0-9_$]*\\b"
                }, {
                    token : "keyword.operator",
                    regex : "!|\\$|%|&|\\*|\\-\\-|\\-|\\+\\+|\\+|~|===|==|=|!=|!==|<=|>=|<<=|>>=|>>>=|<>|<|>|!|&&|\\|\\||\\?\\:|\\*=|%=|\\+=|\\-=|&=|\\^=|\\b(?:in|instanceof|new|delete|typeof|void)"
                }, {
                    token : "paren.lparen",
                    regex : "[[({]"
                }, {
                    token : "paren.rparen",
                    regex : "[\\])}]"
                }, {
                    token : "text",
                    regex : "\\s+"
                }
            ],
            "comment" : [
                {
                    token : "comment", // closing comment
                    regex : "\\*\\/",
                    next : "start"
                }, {
                    defaultToken : "comment"
                }
            ],
            "string" : [
                {
                    token : "escape",
                    regex : '\\\\"'
                }, {
                    token : "string",
                    regex : '"',
                    next : "start"
                }, {
                    token : "string.invalid",
                    regex : '[^"\\\\]*$',
                    next : "start"
                }, {
                    token : "string",
                    regex : '[^"\\\\]+'
                }
            ],
            "tstring" : [
                {
                    token : "string",
                    regex : '"{3,5}',
                    next : "start"
                }, {
                    defaultToken : "string"
                }
            ]
        };

        this.embedRules(DocCommentHighlightRules, "doc-",
            [ DocCommentHighlightRules.getEndRule("start") ]);
    };

    oop.inherits(ScalaHighlightRules, TextHighlightRules);

    exports.ScalaHighlightRules = ScalaHighlightRules;
});

define("ace/mode/scala",["require","exports","module","ace/lib/oop","ace/mode/javascript","ace/mode/scala_highlight_rules"], function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var JavaScriptMode = require("./javascript").Mode;
    var ScalaHighlightRules = require("./scala_highlight_rules").ScalaHighlightRules;

    var Mode = function() {
        JavaScriptMode.call(this);
        this.HighlightRules = ScalaHighlightRules;
    };
    oop.inherits(Mode, JavaScriptMode);

    (function() {

        this.createWorker = function(session) {
            return null;
        };

        this.$id = "ace/mode/scala";
    }).call(Mode.prototype);

    exports.Mode = Mode;
});                (function() {
    window.require(["ace/mode/scala"], function(m) {
        if (typeof module == "object" && typeof exports == "object" && module) {
            module.exports = m;
        }
    });
})();

// ------------------------------------------------------- [BEGIN JAVA SECTION] -------------------------------------------------------

define("ace/mode/java_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/doc_comment_highlight_rules","ace/mode/text_highlight_rules"], function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var DocCommentHighlightRules = require("./doc_comment_highlight_rules").DocCommentHighlightRules;
    var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;

    var JavaHighlightRules = function() {
        var keywords = (
            "abstract|continue|for|new|switch|" +
            "assert|default|goto|package|synchronized|" +
            "boolean|do|if|private|this|" +
            "break|double|implements|protected|throw|" +
            "byte|else|import|public|throws|" +
            "case|enum|instanceof|return|transient|" +
            "catch|extends|int|short|try|" +
            "char|final|interface|static|void|" +
            "class|finally|long|strictfp|volatile|" +
            "const|float|native|super|while|" +
            "var"
        );

        var buildinConstants = ("null|Infinity|NaN|undefined");


        var langClasses = (
            "AbstractMethodError|AssertionError|ClassCircularityError|"+
            "ClassFormatError|Deprecated|EnumConstantNotPresentException|"+
            "ExceptionInInitializerError|IllegalAccessError|"+
            "IllegalThreadStateException|InstantiationError|InternalError|"+
            "NegativeArraySizeException|NoSuchFieldError|Override|Process|"+
            "ProcessBuilder|SecurityManager|StringIndexOutOfBoundsException|"+
            "SuppressWarnings|TypeNotPresentException|UnknownError|"+
            "UnsatisfiedLinkError|UnsupportedClassVersionError|VerifyError|"+
            "InstantiationException|IndexOutOfBoundsException|"+
            "ArrayIndexOutOfBoundsException|CloneNotSupportedException|"+
            "NoSuchFieldException|IllegalArgumentException|NumberFormatException|"+
            "SecurityException|Void|InheritableThreadLocal|IllegalStateException|"+
            "InterruptedException|NoSuchMethodException|IllegalAccessException|"+
            "UnsupportedOperationException|Enum|StrictMath|Package|Compiler|"+
            "Readable|Runtime|StringBuilder|Math|IncompatibleClassChangeError|"+
            "NoSuchMethodError|ThreadLocal|RuntimePermission|ArithmeticException|"+
            "NullPointerException|Long|Integer|Short|Byte|Double|Number|Float|"+
            "Character|Boolean|StackTraceElement|Appendable|StringBuffer|"+
            "Iterable|ThreadGroup|Runnable|Thread|IllegalMonitorStateException|"+
            "StackOverflowError|OutOfMemoryError|VirtualMachineError|"+
            "ArrayStoreException|ClassCastException|LinkageError|"+
            "NoClassDefFoundError|ClassNotFoundException|RuntimeException|"+
            "Exception|ThreadDeath|Error|Throwable|System|ClassLoader|"+
            "Cloneable|Class|CharSequence|Comparable|String|Object"
        );

        var keywordMapper = this.createKeywordMapper({
            "variable.language": "this",
            "keyword": keywords,
            "constant.language": buildinConstants,
            "support.function": langClasses
        }, "identifier");

        this.$rules = {
            "start" : [
                {
                    token : "comment",
                    regex : "\\/\\/.*$"
                },
                DocCommentHighlightRules.getStartRule("doc-start"),
                {
                    token : "comment", // multi line comment
                    regex : "\\/\\*",
                    next : "comment"
                }, {
                    token : "string", // single line
                    regex : '["](?:(?:\\\\.)|(?:[^"\\\\]))*?["]'
                }, {
                    token : "string", // single line
                    regex : "['](?:(?:\\\\.)|(?:[^'\\\\]))*?[']"
                }, {
                    token : "constant.numeric", // hex
                    regex : /0(?:[xX][0-9a-fA-F][0-9a-fA-F_]*|[bB][01][01_]*)[LlSsDdFfYy]?\b/
                }, {
                    token : "constant.numeric", // float
                    regex : /[+-]?\d[\d_]*(?:(?:\.[\d_]*)?(?:[eE][+-]?[\d_]+)?)?[LlSsDdFfYy]?\b/
                }, {
                    token : "constant.language.boolean",
                    regex : "(?:true|false)\\b"
                }, {
                    regex: "(open(?:\\s+))?module(?=\\s*\\w)",
                    token: "keyword",
                    next: [{
                        regex: "{",
                        token: "paren.lparen",
                        next: [{
                            regex: "}",
                            token: "paren.rparen",
                            next: "start"
                        }, {
                            regex: "\\b(requires|transitive|exports|opens|to|uses|provides|with)\\b",
                            token: "keyword"
                        }]
                    }, {
                        token : "text",
                        regex : "\\s+"
                    }, {
                        token : "identifier",
                        regex : "\\w+"
                    }, {
                        token : "punctuation.operator",
                        regex : "."
                    }, {
                        token : "text",
                        regex : "\\s+"
                    }, {
                        regex: "", // exit if there is anything else
                        next: "start"
                    }]
                }, {
                    token : keywordMapper,
                    regex : "[a-zA-Z_$][a-zA-Z0-9_$]*\\b"
                }, {
                    token : "keyword.operator",
                    regex : "!|\\$|%|&|\\*|\\-\\-|\\-|\\+\\+|\\+|~|===|==|=|!=|!==|<=|>=|<<=|>>=|>>>=|<>|<|>|!|&&|\\|\\||\\?\\:|\\*=|%=|\\+=|\\-=|&=|\\^=|\\b(?:in|instanceof|new|delete|typeof|void)"
                }, {
                    token : "lparen",
                    regex : "[[({]"
                }, {
                    token : "rparen",
                    regex : "[\\])}]"
                }, {
                    token : "text",
                    regex : "\\s+"
                }
            ],
            "comment" : [
                {
                    token : "comment", // closing comment
                    regex : "\\*\\/",
                    next : "start"
                }, {
                    defaultToken : "comment"
                }
            ]
        };


        this.embedRules(DocCommentHighlightRules, "doc-",
            [ DocCommentHighlightRules.getEndRule("start") ]);
        this.normalizeRules();
    };

    oop.inherits(JavaHighlightRules, TextHighlightRules);

    exports.JavaHighlightRules = JavaHighlightRules;
});

define("ace/mode/folding/java",["require","exports","module","ace/lib/oop","ace/mode/folding/cstyle","ace/range"], function(require, exports, module) {
    "use strict";

    var oop = require("../../lib/oop");
    var CStyleFoldMode = require("./cstyle").FoldMode;
    var Range = require("../../range").Range;

    var FoldMode = exports.FoldMode = function() {};
    oop.inherits(FoldMode, CStyleFoldMode);

    (function() {
        this.importRegex = /^import /;
        this.getCStyleFoldWidget = this.getFoldWidget;
        this.getFoldWidget = function(session, foldStyle, row) {
            if (foldStyle === "markbegin") {
                var line = session.getLine(row);
                if (this.importRegex.test(line)) {
                    if (row == 0 || !this.importRegex.test(session.getLine(row - 1)))
                        return "start";
                }
            }

            return this.getCStyleFoldWidget(session, foldStyle, row);
        };

        this.getCstyleFoldWidgetRange = this.getFoldWidgetRange;
        this.getFoldWidgetRange = function(session, foldStyle, row, forceMultiline) {
            var line = session.getLine(row);
            var match = line.match(this.importRegex);
            if (!match || foldStyle !== "markbegin")
                return this.getCstyleFoldWidgetRange(session, foldStyle, row, forceMultiline);

            var startColumn = match[0].length;
            var maxRow = session.getLength();
            var startRow = row;
            var endRow = row;

            while (++row < maxRow) {
                var line = session.getLine(row);
                if (line.match(/^\s*$/))
                    continue;

                if (!line.match(this.importRegex))
                    break;

                endRow = row;
            }

            if (endRow > startRow) {
                var endColumn = session.getLine(endRow).length;
                return new Range(startRow, startColumn, endRow, endColumn);
            }
        };

    }).call(FoldMode.prototype);

});

define("ace/mode/java",["require","exports","module","ace/lib/oop","ace/mode/javascript","ace/mode/java_highlight_rules","ace/mode/folding/java"], function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var JavaScriptMode = require("./javascript").Mode;
    var JavaHighlightRules = require("./java_highlight_rules").JavaHighlightRules;
    var JavaFoldMode = require("./folding/java").FoldMode;

    var Mode = function() {
        JavaScriptMode.call(this);
        this.HighlightRules = JavaHighlightRules;
        this.foldingRules = new JavaFoldMode();
    };
    oop.inherits(Mode, JavaScriptMode);

    (function() {

        this.createWorker = function(session) {
            return null;
        };

        this.$id = "ace/mode/java";
    }).call(Mode.prototype);

    exports.Mode = Mode;
});                (function() {
    window.require(["ace/mode/java"], function(m) {
        if (typeof module == "object" && typeof exports == "object" && module) {
            module.exports = m;
        }
    });
})();

// ------------------------------------------------------- [BEGIN KOTLIN SECTION] -------------------------------------------------------

define("ace/mode/kotlin_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;

    var KotlinHighlightRules = function() {

        this.$rules = {
            start: [{
                include: "#comments"
            }, {
                token: [
                    "text",
                    "keyword.other.kotlin",
                    "text",
                    "entity.name.package.kotlin",
                    "text"
                ],
                regex: /^(\s*)(package)\b(?:(\s*)([^ ;$]+)(\s*))?/
            }, {
                include: "#imports"
            }, {
                include: "#statements"
            }],
            "#classes": [{
                token: "text",
                regex: /(?=\s*(?:companion|class|object|interface))/,
                push: [{
                    token: "text",
                    regex: /}|(?=$)/,
                    next: "pop"
                }, {
                    token: ["keyword.other.kotlin", "text"],
                    regex: /\b((?:companion\s*)?)(class|object|interface)\b/,
                    push: [{
                        token: "text",
                        regex: /(?=<|{|\(|:)/,
                        next: "pop"
                    }, {
                        token: "keyword.other.kotlin",
                        regex: /\bobject\b/
                    }, {
                        token: "entity.name.type.class.kotlin",
                        regex: /\w+/
                    }]
                }, {
                    token: "text",
                    regex: /</,
                    push: [{
                        token: "text",
                        regex: />/,
                        next: "pop"
                    }, {
                        include: "#generics"
                    }]
                }, {
                    token: "text",
                    regex: /\(/,
                    push: [{
                        token: "text",
                        regex: /\)/,
                        next: "pop"
                    }, {
                        include: "#parameters"
                    }]
                }, {
                    token: "keyword.operator.declaration.kotlin",
                    regex: /:/,
                    push: [{
                        token: "text",
                        regex: /(?={|$)/,
                        next: "pop"
                    }, {
                        token: "entity.other.inherited-class.kotlin",
                        regex: /\w+/
                    }, {
                        token: "text",
                        regex: /\(/,
                        push: [{
                            token: "text",
                            regex: /\)/,
                            next: "pop"
                        }, {
                            include: "#expressions"
                        }]
                    }]
                }, {
                    token: "text",
                    regex: /\{/,
                    push: [{
                        token: "text",
                        regex: /\}/,
                        next: "pop"
                    }, {
                        include: "#statements"
                    }]
                }]
            }],
            "#comments": [{
                token: "punctuation.definition.comment.kotlin",
                regex: /\/\*/,
                push: [{
                    token: "punctuation.definition.comment.kotlin",
                    regex: /\*\//,
                    next: "pop"
                }, {
                    defaultToken: "comment.block.kotlin"
                }]
            }, {
                token: [
                    "text",
                    "punctuation.definition.comment.kotlin",
                    "comment.line.double-slash.kotlin"
                ],
                regex: /(\s*)(\/\/)(.*$)/
            }],
            "#constants": [{
                token: "constant.language.kotlin",
                regex: /\b(?:true|false|null|this|super)\b/
            }, {
                token: "constant.numeric.kotlin",
                regex: /\b(?:0(?:x|X)[0-9a-fA-F]*|(?:[0-9]+\.?[0-9]*|\.[0-9]+)(?:(?:e|E)(?:\+|-)?[0-9]+)?)(?:[LlFfUuDd]|UL|ul)?\b/
            }, {
                token: "constant.other.kotlin",
                regex: /\b[A-Z][A-Z0-9_]+\b/
            }],
            "#expressions": [{
                token: "text",
                regex: /\(/,
                push: [{
                    token: "text",
                    regex: /\)/,
                    next: "pop"
                }, {
                    include: "#expressions"
                }]
            }, {
                include: "#types"
            }, {
                include: "#strings"
            }, {
                include: "#constants"
            }, {
                include: "#comments"
            }, {
                include: "#keywords"
            }],
            "#functions": [{
                token: "text",
                regex: /(?=\s*fun)/,
                push: [{
                    token: "text",
                    regex: /}|(?=$)/,
                    next: "pop"
                }, {
                    token: "keyword.other.kotlin",
                    regex: /\bfun\b/,
                    push: [{
                        token: "text",
                        regex: /(?=\()/,
                        next: "pop"
                    }, {
                        token: "text",
                        regex: /</,
                        push: [{
                            token: "text",
                            regex: />/,
                            next: "pop"
                        }, {
                            include: "#generics"
                        }]
                    }, {
                        token: ["text", "entity.name.function.kotlin"],
                        regex: /((?:[\.<\?>\w]+\.)?)(\w+)/
                    }]
                }, {
                    token: "text",
                    regex: /\(/,
                    push: [{
                        token: "text",
                        regex: /\)/,
                        next: "pop"
                    }, {
                        include: "#parameters"
                    }]
                }, {
                    token: "keyword.operator.declaration.kotlin",
                    regex: /:/,
                    push: [{
                        token: "text",
                        regex: /(?={|=|$)/,
                        next: "pop"
                    }, {
                        include: "#types"
                    }]
                }, {
                    token: "text",
                    regex: /\{/,
                    push: [{
                        token: "text",
                        regex: /(?=\})/,
                        next: "pop"
                    }, {
                        include: "#statements"
                    }]
                }, {
                    token: "keyword.operator.assignment.kotlin",
                    regex: /=/,
                    push: [{
                        token: "text",
                        regex: /(?=$)/,
                        next: "pop"
                    }, {
                        include: "#expressions"
                    }]
                }]
            }],
            "#generics": [{
                token: "keyword.operator.declaration.kotlin",
                regex: /:/,
                push: [{
                    token: "text",
                    regex: /(?=,|>)/,
                    next: "pop"
                }, {
                    include: "#types"
                }]
            }, {
                include: "#keywords"
            }, {
                token: "storage.type.generic.kotlin",
                regex: /\w+/
            }],
            "#getters-and-setters": [{
                token: ["entity.name.function.kotlin", "text"],
                regex: /\b(get)\b(\s*\(\s*\))/,
                push: [{
                    token: "text",
                    regex: /\}|(?=\bset\b)|$/,
                    next: "pop"
                }, {
                    token: "keyword.operator.assignment.kotlin",
                    regex: /=/,
                    push: [{
                        token: "text",
                        regex: /(?=$|\bset\b)/,
                        next: "pop"
                    }, {
                        include: "#expressions"
                    }]
                }, {
                    token: "text",
                    regex: /\{/,
                    push: [{
                        token: "text",
                        regex: /\}/,
                        next: "pop"
                    }, {
                        include: "#expressions"
                    }]
                }]
            }, {
                token: ["entity.name.function.kotlin", "text"],
                regex: /\b(set)\b(\s*)(?=\()/,
                push: [{
                    token: "text",
                    regex: /\}|(?=\bget\b)|$/,
                    next: "pop"
                }, {
                    token: "text",
                    regex: /\(/,
                    push: [{
                        token: "text",
                        regex: /\)/,
                        next: "pop"
                    }, {
                        include: "#parameters"
                    }]
                }, {
                    token: "keyword.operator.assignment.kotlin",
                    regex: /=/,
                    push: [{
                        token: "text",
                        regex: /(?=$|\bset\b)/,
                        next: "pop"
                    }, {
                        include: "#expressions"
                    }]
                }, {
                    token: "text",
                    regex: /\{/,
                    push: [{
                        token: "text",
                        regex: /\}/,
                        next: "pop"
                    }, {
                        include: "#expressions"
                    }]
                }]
            }],
            "#imports": [{
                token: [
                    "text",
                    "keyword.other.kotlin",
                    "text",
                    "keyword.other.kotlin"
                ],
                regex: /^(\s*)(import)(\s+[^ $]+\s+)((?:as)?)/
            }],
            "#keywords": [{
                token: "storage.modifier.kotlin",
                regex: /\b(?:var|val|public|private|protected|abstract|final|enum|open|attribute|annotation|override|inline|var|val|vararg|lazy|in|out|internal|data|tailrec|operator|infix|const|yield|typealias|typeof)\b/
            }, {
                token: "keyword.control.catch-exception.kotlin",
                regex: /\b(?:try|catch|finally|throw)\b/
            }, {
                token: "keyword.control.kotlin",
                regex: /\b(?:if|else|while|for|do|return|when|where|break|continue)\b/
            }, {
                token: "keyword.operator.kotlin",
                regex: /\b(?:in|is|as|assert)\b/
            }, {
                token: "keyword.operator.comparison.kotlin",
                regex: /==|!=|===|!==|<=|>=|<|>/
            }, {
                token: "keyword.operator.assignment.kotlin",
                regex: /=/
            }, {
                token: "keyword.operator.declaration.kotlin",
                regex: /:/
            }, {
                token: "keyword.operator.dot.kotlin",
                regex: /\./
            }, {
                token: "keyword.operator.increment-decrement.kotlin",
                regex: /\-\-|\+\+/
            }, {
                token: "keyword.operator.arithmetic.kotlin",
                regex: /\-|\+|\*|\/|%/
            }, {
                token: "keyword.operator.arithmetic.assign.kotlin",
                regex: /\+=|\-=|\*=|\/=/
            }, {
                token: "keyword.operator.logical.kotlin",
                regex: /!|&&|\|\|/
            }, {
                token: "keyword.operator.range.kotlin",
                regex: /\.\./
            }, {
                token: "punctuation.terminator.kotlin",
                regex: /;/
            }],
            "#namespaces": [{
                token: "keyword.other.kotlin",
                regex: /\bnamespace\b/
            }, {
                token: "text",
                regex: /\{/,
                push: [{
                    token: "text",
                    regex: /\}/,
                    next: "pop"
                }, {
                    include: "#statements"
                }]
            }],
            "#parameters": [{
                token: "keyword.operator.declaration.kotlin",
                regex: /:/,
                push: [{
                    token: "text",
                    regex: /(?=,|\)|=)/,
                    next: "pop"
                }, {
                    include: "#types"
                }]
            }, {
                token: "keyword.operator.declaration.kotlin",
                regex: /=/,
                push: [{
                    token: "text",
                    regex: /(?=,|\))/,
                    next: "pop"
                }, {
                    include: "#expressions"
                }]
            }, {
                include: "#keywords"
            }, {
                token: "variable.parameter.function.kotlin",
                regex: /\w+/
            }],
            "#statements": [{
                include: "#namespaces"
            }, {
                include: "#typedefs"
            }, {
                include: "#classes"
            }, {
                include: "#functions"
            }, {
                include: "#variables"
            }, {
                include: "#getters-and-setters"
            }, {
                include: "#expressions"
            }],
            "#strings": [{
                token: "punctuation.definition.string.begin.kotlin",
                regex: /"""/,
                push: [{
                    token: "punctuation.definition.string.end.kotlin",
                    regex: /"""/,
                    next: "pop"
                }, {
                    token: "variable.parameter.template.kotlin",
                    regex: /\$\w+|\$\{[^\}]+\}/
                }, {
                    token: "constant.character.escape.kotlin",
                    regex: /\\./
                }, {
                    defaultToken: "string.quoted.third.kotlin"
                }]
            }, {
                token: "punctuation.definition.string.begin.kotlin",
                regex: /"/,
                push: [{
                    token: "punctuation.definition.string.end.kotlin",
                    regex: /"/,
                    next: "pop"
                }, {
                    token: "variable.parameter.template.kotlin",
                    regex: /\$\w+|\$\{[^\}]+\}/
                }, {
                    token: "constant.character.escape.kotlin",
                    regex: /\\./
                }, {
                    defaultToken: "string.quoted.double.kotlin"
                }]
            }, {
                token: "punctuation.definition.string.begin.kotlin",
                regex: /'/,
                push: [{
                    token: "punctuation.definition.string.end.kotlin",
                    regex: /'/,
                    next: "pop"
                }, {
                    token: "constant.character.escape.kotlin",
                    regex: /\\./
                }, {
                    defaultToken: "string.quoted.single.kotlin"
                }]
            }, {
                token: "punctuation.definition.string.begin.kotlin",
                regex: /`/,
                push: [{
                    token: "punctuation.definition.string.end.kotlin",
                    regex: /`/,
                    next: "pop"
                }, {
                    defaultToken: "string.quoted.single.kotlin"
                }]
            }],
            "#typedefs": [{
                token: "text",
                regex: /(?=\s*type)/,
                push: [{
                    token: "text",
                    regex: /(?=$)/,
                    next: "pop"
                }, {
                    token: "keyword.other.kotlin",
                    regex: /\btype\b/
                }, {
                    token: "text",
                    regex: /</,
                    push: [{
                        token: "text",
                        regex: />/,
                        next: "pop"
                    }, {
                        include: "#generics"
                    }]
                }, {
                    include: "#expressions"
                }]
            }],
            "#types": [{
                token: "storage.type.buildin.kotlin",
                regex: /\b(?:Any|Unit|String|Int|Boolean|Char|Long|Double|Float|Short|Byte|dynamic)\b/
            }, {
                token: "storage.type.buildin.array.kotlin",
                regex: /\b(?:IntArray|BooleanArray|CharArray|LongArray|DoubleArray|FloatArray|ShortArray|ByteArray)\b/
            }, {
                token: [
                    "storage.type.buildin.collection.kotlin",
                    "text"
                ],
                regex: /\b(Array|List|Map)(<\b)/,
                push: [{
                    token: "text",
                    regex: />/,
                    next: "pop"
                }, {
                    include: "#types"
                }, {
                    include: "#keywords"
                }]
            }, {
                token: "text",
                regex: /\w+</,
                push: [{
                    token: "text",
                    regex: />/,
                    next: "pop"
                }, {
                    include: "#types"
                }, {
                    include: "#keywords"
                }]
            }, {
                token: ["keyword.operator.tuple.kotlin", "text"],
                regex: /(#)(\()/,
                push: [{
                    token: "text",
                    regex: /\)/,
                    next: "pop"
                }, {
                    include: "#expressions"
                }]
            }, {
                token: "text",
                regex: /\{/,
                push: [{
                    token: "text",
                    regex: /\}/,
                    next: "pop"
                }, {
                    include: "#statements"
                }]
            }, {
                token: "text",
                regex: /\(/,
                push: [{
                    token: "text",
                    regex: /\)/,
                    next: "pop"
                }, {
                    include: "#types"
                }]
            }, {
                token: "keyword.operator.declaration.kotlin",
                regex: /->/
            }],
            "#variables": [{
                token: "text",
                regex: /(?=\s*(?:var|val))/,
                push: [{
                    token: "text",
                    regex: /(?=:|=|$)/,
                    next: "pop"
                }, {
                    token: "keyword.other.kotlin",
                    regex: /\b(?:var|val)\b/,
                    push: [{
                        token: "text",
                        regex: /(?=:|=|$)/,
                        next: "pop"
                    }, {
                        token: "text",
                        regex: /</,
                        push: [{
                            token: "text",
                            regex: />/,
                            next: "pop"
                        }, {
                            include: "#generics"
                        }]
                    }, {
                        token: ["text", "entity.name.variable.kotlin"],
                        regex: /((?:[\.<\?>\w]+\.)?)(\w+)/
                    }]
                }, {
                    token: "keyword.operator.declaration.kotlin",
                    regex: /:/,
                    push: [{
                        token: "text",
                        regex: /(?==|$)/,
                        next: "pop"
                    }, {
                        include: "#types"
                    }, {
                        include: "#getters-and-setters"
                    }]
                }, {
                    token: "keyword.operator.assignment.kotlin",
                    regex: /=/,
                    push: [{
                        token: "text",
                        regex: /(?=$)/,
                        next: "pop"
                    }, {
                        include: "#expressions"
                    }, {
                        include: "#getters-and-setters"
                    }]
                }]
            }]
        };

        this.normalizeRules();
    };

    KotlinHighlightRules.metaData = {
        fileTypes: ["kt", "kts"],
        name: "Kotlin",
        scopeName: "source.Kotlin"
    };


    oop.inherits(KotlinHighlightRules, TextHighlightRules);

    exports.KotlinHighlightRules = KotlinHighlightRules;
});

define("ace/mode/folding/cstyle",["require","exports","module","ace/lib/oop","ace/range","ace/mode/folding/fold_mode"], function(require, exports, module) {
    "use strict";

    var oop = require("../../lib/oop");
    var Range = require("../../range").Range;
    var BaseFoldMode = require("./fold_mode").FoldMode;

    var FoldMode = exports.FoldMode = function(commentRegex) {
        if (commentRegex) {
            this.foldingStartMarker = new RegExp(
                this.foldingStartMarker.source.replace(/\|[^|]*?$/, "|" + commentRegex.start)
            );
            this.foldingStopMarker = new RegExp(
                this.foldingStopMarker.source.replace(/\|[^|]*?$/, "|" + commentRegex.end)
            );
        }
    };
    oop.inherits(FoldMode, BaseFoldMode);

    (function() {

        this.foldingStartMarker = /([\{\[\(])[^\}\]\)]*$|^\s*(\/\*)/;
        this.foldingStopMarker = /^[^\[\{\(]*([\}\]\)])|^[\s\*]*(\*\/)/;
        this.singleLineBlockCommentRe= /^\s*(\/\*).*\*\/\s*$/;
        this.tripleStarBlockCommentRe = /^\s*(\/\*\*\*).*\*\/\s*$/;
        this.startRegionRe = /^\s*(\/\*|\/\/)#?region\b/;
        this._getFoldWidgetBase = this.getFoldWidget;
        this.getFoldWidget = function(session, foldStyle, row) {
            var line = session.getLine(row);

            if (this.singleLineBlockCommentRe.test(line)) {
                if (!this.startRegionRe.test(line) && !this.tripleStarBlockCommentRe.test(line))
                    return "";
            }

            var fw = this._getFoldWidgetBase(session, foldStyle, row);

            if (!fw && this.startRegionRe.test(line))
                return "start"; // lineCommentRegionStart

            return fw;
        };

        this.getFoldWidgetRange = function(session, foldStyle, row, forceMultiline) {
            var line = session.getLine(row);

            if (this.startRegionRe.test(line))
                return this.getCommentRegionBlock(session, line, row);

            var match = line.match(this.foldingStartMarker);
            if (match) {
                var i = match.index;

                if (match[1])
                    return this.openingBracketBlock(session, match[1], row, i);

                var range = session.getCommentFoldRange(row, i + match[0].length, 1);

                if (range && !range.isMultiLine()) {
                    if (forceMultiline) {
                        range = this.getSectionRange(session, row);
                    } else if (foldStyle != "all")
                        range = null;
                }

                return range;
            }

            if (foldStyle === "markbegin")
                return;

            var match = line.match(this.foldingStopMarker);
            if (match) {
                var i = match.index + match[0].length;

                if (match[1])
                    return this.closingBracketBlock(session, match[1], row, i);

                return session.getCommentFoldRange(row, i, -1);
            }
        };

        this.getSectionRange = function(session, row) {
            var line = session.getLine(row);
            var startIndent = line.search(/\S/);
            var startRow = row;
            var startColumn = line.length;
            row = row + 1;
            var endRow = row;
            var maxRow = session.getLength();
            while (++row < maxRow) {
                line = session.getLine(row);
                var indent = line.search(/\S/);
                if (indent === -1)
                    continue;
                if  (startIndent > indent)
                    break;
                var subRange = this.getFoldWidgetRange(session, "all", row);

                if (subRange) {
                    if (subRange.start.row <= startRow) {
                        break;
                    } else if (subRange.isMultiLine()) {
                        row = subRange.end.row;
                    } else if (startIndent == indent) {
                        break;
                    }
                }
                endRow = row;
            }

            return new Range(startRow, startColumn, endRow, session.getLine(endRow).length);
        };
        this.getCommentRegionBlock = function(session, line, row) {
            var startColumn = line.search(/\s*$/);
            var maxRow = session.getLength();
            var startRow = row;

            var re = /^\s*(?:\/\*|\/\/|--)#?(end)?region\b/;
            var depth = 1;
            while (++row < maxRow) {
                line = session.getLine(row);
                var m = re.exec(line);
                if (!m) continue;
                if (m[1]) depth--;
                else depth++;

                if (!depth) break;
            }

            var endRow = row;
            if (endRow > startRow) {
                return new Range(startRow, startColumn, endRow, line.length);
            }
        };

    }).call(FoldMode.prototype);

});

define("ace/mode/kotlin",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/kotlin_highlight_rules","ace/mode/behaviour/cstyle","ace/mode/folding/cstyle"], function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var TextMode = require("./text").Mode;
    var KotlinHighlightRules = require("./kotlin_highlight_rules").KotlinHighlightRules;
    var CstyleBehaviour = require("./behaviour/cstyle").CstyleBehaviour;
    var FoldMode = require("./folding/cstyle").FoldMode;

    var Mode = function() {
        this.HighlightRules = KotlinHighlightRules;
        this.foldingRules = new FoldMode();
        this.$behaviour = new CstyleBehaviour();
    };
    oop.inherits(Mode, TextMode);

    (function() {
        this.$id = "ace/mode/kotlin";
    }).call(Mode.prototype);

    exports.Mode = Mode;
});

// ------------------------------------------------------- [BEGIN PYTHON SECTION] -------------------------------------------------------

define("ace/mode/python_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;

    var PythonHighlightRules = function() {

        var keywords = (
            "and|as|assert|break|class|continue|def|del|elif|else|except|exec|" +
            "finally|for|from|global|if|import|in|is|lambda|not|or|pass|print|" +
            "raise|return|try|while|with|yield|async|await|nonlocal"
        );

        var builtinConstants = (
            "True|False|None|NotImplemented|Ellipsis|__debug__"
        );

        var builtinFunctions = (
            "abs|divmod|input|open|staticmethod|all|enumerate|int|ord|str|any|" +
            "eval|isinstance|pow|sum|basestring|execfile|issubclass|print|super|" +
            "binfile|bin|iter|property|tuple|bool|filter|len|range|type|bytearray|" +
            "float|list|raw_input|unichr|callable|format|locals|reduce|unicode|" +
            "chr|frozenset|long|reload|vars|classmethod|getattr|map|repr|xrange|" +
            "cmp|globals|max|reversed|zip|compile|hasattr|memoryview|round|" +
            "__import__|complex|hash|min|apply|delattr|help|next|setattr|set|" +
            "buffer|dict|hex|object|slice|coerce|dir|id|oct|sorted|intern|" +
            "ascii|breakpoint|bytes"
        );
        var keywordMapper = this.createKeywordMapper({
            "invalid.deprecated": "debugger",
            "support.function": builtinFunctions,
            "variable.language": "self|cls",
            "constant.language": builtinConstants,
            "keyword": keywords
        }, "identifier");

        var strPre = "[uU]?";
        var strRawPre = "[rR]";
        var strFormatPre = "[fF]";
        var strRawFormatPre = "(?:[rR][fF]|[fF][rR])";
        var decimalInteger = "(?:(?:[1-9]\\d*)|(?:0))";
        var octInteger = "(?:0[oO]?[0-7]+)";
        var hexInteger = "(?:0[xX][\\dA-Fa-f]+)";
        var binInteger = "(?:0[bB][01]+)";
        var integer = "(?:" + decimalInteger + "|" + octInteger + "|" + hexInteger + "|" + binInteger + ")";

        var exponent = "(?:[eE][+-]?\\d+)";
        var fraction = "(?:\\.\\d+)";
        var intPart = "(?:\\d+)";
        var pointFloat = "(?:(?:" + intPart + "?" + fraction + ")|(?:" + intPart + "\\.))";
        var exponentFloat = "(?:(?:" + pointFloat + "|" + intPart + ")" + exponent + ")";
        var floatNumber = "(?:" + exponentFloat + "|" + pointFloat + ")";

        var stringEscape = "\\\\(x[0-9A-Fa-f]{2}|[0-7]{3}|[\\\\abfnrtv'\"]|U[0-9A-Fa-f]{8}|u[0-9A-Fa-f]{4})";

        this.$rules = {
            "start" : [ {
                token : "comment",
                regex : "#.*$"
            }, {
                token : "string",           // multi line """ string start
                regex : strPre + '"{3}',
                next : "qqstring3"
            }, {
                token : "string",           // " string
                regex : strPre + '"(?=.)',
                next : "qqstring"
            }, {
                token : "string",           // multi line ''' string start
                regex : strPre + "'{3}",
                next : "qstring3"
            }, {
                token : "string",           // ' string
                regex : strPre + "'(?=.)",
                next : "qstring"
            }, {
                token: "string",
                regex: strRawPre + '"{3}',
                next: "rawqqstring3"
            }, {
                token: "string",
                regex: strRawPre + '"(?=.)',
                next: "rawqqstring"
            }, {
                token: "string",
                regex: strRawPre + "'{3}",
                next: "rawqstring3"
            }, {
                token: "string",
                regex: strRawPre + "'(?=.)",
                next: "rawqstring"
            }, {
                token: "string",
                regex: strFormatPre + '"{3}',
                next: "fqqstring3"
            }, {
                token: "string",
                regex: strFormatPre + '"(?=.)',
                next: "fqqstring"
            }, {
                token: "string",
                regex: strFormatPre + "'{3}",
                next: "fqstring3"
            }, {
                token: "string",
                regex: strFormatPre + "'(?=.)",
                next: "fqstring"
            },{
                token: "string",
                regex: strRawFormatPre + '"{3}',
                next: "rfqqstring3"
            }, {
                token: "string",
                regex: strRawFormatPre + '"(?=.)',
                next: "rfqqstring"
            }, {
                token: "string",
                regex: strRawFormatPre + "'{3}",
                next: "rfqstring3"
            }, {
                token: "string",
                regex: strRawFormatPre + "'(?=.)",
                next: "rfqstring"
            }, {
                token: "keyword.operator",
                regex: "\\+|\\-|\\*|\\*\\*|\\/|\\/\\/|%|@|<<|>>|&|\\||\\^|~|<|>|<=|=>|==|!=|<>|="
            }, {
                token: "punctuation",
                regex: ",|:|;|\\->|\\+=|\\-=|\\*=|\\/=|\\/\\/=|%=|@=|&=|\\|=|^=|>>=|<<=|\\*\\*="
            }, {
                token: "paren.lparen",
                regex: "[\\[\\(\\{]"
            }, {
                token: "paren.rparen",
                regex: "[\\]\\)\\}]"
            }, {
                token: "text",
                regex: "\\s+"
            }, {
                include: "constants"
            }],
            "qqstring3": [{
                token: "constant.language.escape",
                regex: stringEscape
            }, {
                token: "string", // multi line """ string end
                regex: '"{3}',
                next: "start"
            }, {
                defaultToken: "string"
            }],
            "qstring3": [{
                token: "constant.language.escape",
                regex: stringEscape
            }, {
                token: "string",  // multi line ''' string end
                regex: "'{3}",
                next: "start"
            }, {
                defaultToken: "string"
            }],
            "qqstring": [{
                token: "constant.language.escape",
                regex: stringEscape
            }, {
                token: "string",
                regex: "\\\\$",
                next: "qqstring"
            }, {
                token: "string",
                regex: '"|$',
                next: "start"
            }, {
                defaultToken: "string"
            }],
            "qstring": [{
                token: "constant.language.escape",
                regex: stringEscape
            }, {
                token: "string",
                regex: "\\\\$",
                next: "qstring"
            }, {
                token: "string",
                regex: "'|$",
                next: "start"
            }, {
                defaultToken: "string"
            }],
            "rawqqstring3": [{
                token: "string", // multi line """ string end
                regex: '"{3}',
                next: "start"
            }, {
                defaultToken: "string"
            }],
            "rawqstring3": [{
                token: "string",  // multi line ''' string end
                regex: "'{3}",
                next: "start"
            }, {
                defaultToken: "string"
            }],
            "rawqqstring": [{
                token: "string",
                regex: "\\\\$",
                next: "rawqqstring"
            }, {
                token: "string",
                regex: '"|$',
                next: "start"
            }, {
                defaultToken: "string"
            }],
            "rawqstring": [{
                token: "string",
                regex: "\\\\$",
                next: "rawqstring"
            }, {
                token: "string",
                regex: "'|$",
                next: "start"
            }, {
                defaultToken: "string"
            }],
            "fqqstring3": [{
                token: "constant.language.escape",
                regex: stringEscape
            }, {
                token: "string", // multi line """ string end
                regex: '"{3}',
                next: "start"
            }, {
                token: "paren.lparen",
                regex: "{",
                push: "fqstringParRules"
            }, {
                defaultToken: "string"
            }],
            "fqstring3": [{
                token: "constant.language.escape",
                regex: stringEscape
            }, {
                token: "string",  // multi line ''' string end
                regex: "'{3}",
                next: "start"
            }, {
                token: "paren.lparen",
                regex: "{",
                push: "fqstringParRules"
            }, {
                defaultToken: "string"
            }],
            "fqqstring": [{
                token: "constant.language.escape",
                regex: stringEscape
            }, {
                token: "string",
                regex: "\\\\$",
                next: "fqqstring"
            }, {
                token: "string",
                regex: '"|$',
                next: "start"
            }, {
                token: "paren.lparen",
                regex: "{",
                push: "fqstringParRules"
            }, {
                defaultToken: "string"
            }],
            "fqstring": [{
                token: "constant.language.escape",
                regex: stringEscape
            }, {
                token: "string",
                regex: "'|$",
                next: "start"
            }, {
                token: "paren.lparen",
                regex: "{",
                push: "fqstringParRules"
            }, {
                defaultToken: "string"
            }],
            "rfqqstring3": [{
                token: "string", // multi line """ string end
                regex: '"{3}',
                next: "start"
            }, {
                token: "paren.lparen",
                regex: "{",
                push: "fqstringParRules"
            }, {
                defaultToken: "string"
            }],
            "rfqstring3": [{
                token: "string",  // multi line ''' string end
                regex: "'{3}",
                next: "start"
            }, {
                token: "paren.lparen",
                regex: "{",
                push: "fqstringParRules"
            }, {
                defaultToken: "string"
            }],
            "rfqqstring": [{
                token: "string",
                regex: "\\\\$",
                next: "rfqqstring"
            }, {
                token: "string",
                regex: '"|$',
                next: "start"
            }, {
                token: "paren.lparen",
                regex: "{",
                push: "fqstringParRules"
            }, {
                defaultToken: "string"
            }],
            "rfqstring": [{
                token: "string",
                regex: "'|$",
                next: "start"
            }, {
                token: "paren.lparen",
                regex: "{",
                push: "fqstringParRules"
            }, {
                defaultToken: "string"
            }],
            "fqstringParRules": [{//TODO: nested {}
                token: "paren.lparen",
                regex: "[\\[\\(]"
            }, {
                token: "paren.rparen",
                regex: "[\\]\\)]"
            }, {
                token: "string",
                regex: "\\s+"
            }, {
                token: "string",
                regex: "'(.)*'"
            }, {
                token: "string",
                regex: '"(.)*"'
            }, {
                token: "function.support",
                regex: "(!s|!r|!a)"
            }, {
                include: "constants"
            },{
                token: 'paren.rparen',
                regex: "}",
                next: 'pop'
            },{
                token: 'paren.lparen',
                regex: "{",
                push: "fqstringParRules"
            }],
            "constants": [{
                token: "constant.numeric", // imaginary
                regex: "(?:" + floatNumber + "|\\d+)[jJ]\\b"
            }, {
                token: "constant.numeric", // float
                regex: floatNumber
            }, {
                token: "constant.numeric", // long integer
                regex: integer + "[lL]\\b"
            }, {
                token: "constant.numeric", // integer
                regex: integer + "\\b"
            }, {
                token: ["punctuation", "function.support"],// method
                regex: "(\\.)([a-zA-Z_]+)\\b"
            }, {
                token: keywordMapper,
                regex: "[a-zA-Z_$][a-zA-Z0-9_$]*\\b"
            }]
        };
        this.normalizeRules();
    };

    oop.inherits(PythonHighlightRules, TextHighlightRules);

    exports.PythonHighlightRules = PythonHighlightRules;
});

define("ace/mode/folding/pythonic",["require","exports","module","ace/lib/oop","ace/mode/folding/fold_mode"], function(require, exports, module) {
    "use strict";

    var oop = require("../../lib/oop");
    var BaseFoldMode = require("./fold_mode").FoldMode;

    var FoldMode = exports.FoldMode = function(markers) {
        this.foldingStartMarker = new RegExp("([\\[{])(?:\\s*)$|(" + markers + ")(?:\\s*)(?:#.*)?$");
    };
    oop.inherits(FoldMode, BaseFoldMode);

    (function() {

        this.getFoldWidgetRange = function(session, foldStyle, row) {
            var line = session.getLine(row);
            var match = line.match(this.foldingStartMarker);
            if (match) {
                if (match[1])
                    return this.openingBracketBlock(session, match[1], row, match.index);
                if (match[2])
                    return this.indentationBlock(session, row, match.index + match[2].length);
                return this.indentationBlock(session, row);
            }
        };

    }).call(FoldMode.prototype);

});

define("ace/mode/python",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/python_highlight_rules","ace/mode/folding/pythonic","ace/range"], function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var TextMode = require("./text").Mode;
    var PythonHighlightRules = require("./python_highlight_rules").PythonHighlightRules;
    var PythonFoldMode = require("./folding/pythonic").FoldMode;
    var Range = require("../range").Range;

    var Mode = function() {
        this.HighlightRules = PythonHighlightRules;
        this.foldingRules = new PythonFoldMode("\\:");
        this.$behaviour = this.$defaultBehaviour;
    };
    oop.inherits(Mode, TextMode);

    (function() {

        this.lineCommentStart = "#";

        this.getNextLineIndent = function(state, line, tab) {
            var indent = this.$getIndent(line);

            var tokenizedLine = this.getTokenizer().getLineTokens(line, state);
            var tokens = tokenizedLine.tokens;

            if (tokens.length && tokens[tokens.length-1].type == "comment") {
                return indent;
            }

            if (state == "start") {
                var match = line.match(/^.*[\{\(\[:]\s*$/);
                if (match) {
                    indent += tab;
                }
            }

            return indent;
        };

        var outdents = {
            "pass": 1,
            "return": 1,
            "raise": 1,
            "break": 1,
            "continue": 1
        };

        this.checkOutdent = function(state, line, input) {
            if (input !== "\r\n" && input !== "\r" && input !== "\n")
                return false;

            var tokens = this.getTokenizer().getLineTokens(line.trim(), state).tokens;

            if (!tokens)
                return false;
            do {
                var last = tokens.pop();
            } while (last && (last.type == "comment" || (last.type == "text" && last.value.match(/^\s+$/))));

            if (!last)
                return false;

            return (last.type == "keyword" && outdents[last.value]);
        };

        this.autoOutdent = function(state, doc, row) {

            row += 1;
            var indent = this.$getIndent(doc.getLine(row));
            var tab = doc.getTabString();
            if (indent.slice(-tab.length) == tab)
                doc.remove(new Range(row, indent.length-tab.length, row, indent.length));
        };

        this.$id = "ace/mode/python";
    }).call(Mode.prototype);

    exports.Mode = Mode;
});                (function() {
    window.require(["ace/mode/python"], function(m) {
        if (typeof module == "object" && typeof exports == "object" && module) {
            module.exports = m;
        }
    });
})();

// ------------------------------------------------------- [END CUSTOM SECTION] -------------------------------------------------------

define("ace/mode/markdown",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/clojure","ace/mode/c_cpp","ace/mode/java", "ace/mode/kotlin","ace/mode/xml","ace/mode/html","ace/mode/markdown_highlight_rules","ace/mode/folding/markdown","ace/mode/javascript","ace/mode/html","ace/mode/sh","ace/mode/sh","ace/mode/xml","ace/mode/css"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var JavaScriptMode = require("./javascript").Mode;
var XmlMode = require("./xml").Mode;
var HtmlMode = require("./html").Mode;
var MarkdownHighlightRules = require("./markdown_highlight_rules").MarkdownHighlightRules;
var MarkdownFoldMode = require("./folding/markdown").FoldMode;

var Mode = function() {
    this.HighlightRules = MarkdownHighlightRules;

    this.createModeDelegates({
        javascript: require("./javascript").Mode,
        html: require("./html").Mode,
        bash: require("./sh").Mode,
        sh: require("./sh").Mode,
        xml: require("./xml").Mode,
        css: require("./css").Mode,
        clojure: require("./clojure").Mode,
        c_cpp: require("./c_cpp").Mode,
        scala: require("./scala").Mode,
        java: require("./java").Mode,
        kotlin: require("./kotlin").Mode,
        python: require("./python").Mode
    });

    this.foldingRules = new MarkdownFoldMode();
    this.$behaviour = this.$defaultBehaviour;
};
oop.inherits(Mode, TextMode);

(function() {
    this.type = "text";
    this.blockComment = {start: "<!--", end: "-->"};

    // ---------- [CUSTOM,LISTS] ----------
    this.getNextLineIndent = function(state, line, tab) {
        let match = /^(\s*)(?:([*+\-$#@&%>])|(\d+)\.)(\s+)(.*)$/.exec(line);
        if (!match)
            return this.$getIndent(line);
        if (!match[5])
            return this.$getIndent(line);
        let marker = match[2];
        if (!marker)
            marker = parseInt(match[3], 10) + 1 + ".";
        return match[1] + marker + match[4];
    };
    this.$id = "ace/mode/markdown";
}).call(Mode.prototype);

exports.Mode = Mode;
});

(function() {
    window.require(["ace/mode/markdown"], function(m) {
        if (typeof module == "object" && typeof exports == "object" && module) {
            module.exports = m;
        }
    });
})();
            