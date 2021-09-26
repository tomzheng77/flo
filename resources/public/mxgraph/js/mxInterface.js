/**
 * wiring between flo and mxgraph, most important functions
 * are init, get-content and set-content
 */

function mxInterfaceInit(container) {
  var editorUiInit = EditorUi.prototype.init;
  var instance = {};
  var editorPromiseResolve = null;
  var editorUiPromiseResolve = null;
  instance.editorPromise = new Promise((resolve, reject) => {
    editorPromiseResolve = resolve;
  });
  instance.editorUiPromise = new Promise((resolve, reject) => {
    editorUiPromiseResolve = resolve;
  });

  EditorUi.prototype.init = function()
  {
    editorUiInit.apply(this, arguments);
    this.actions.get('export').setEnabled(false);

    // Updates action states which require a backend
    // if (!Editor.useLocalStorage)
    // {
    //   mxUtils.post(OPEN_URL, '', mxUtils.bind(this, function(req)
    //   {
    //     var enabled = req.getStatus() != 404;
    //     this.actions.get('open').setEnabled(enabled || Graph.fileSupport);
    //     this.actions.get('import').setEnabled(enabled || Graph.fileSupport);
    //     this.actions.get('save').setEnabled(enabled);
    //     this.actions.get('saveAs').setEnabled(enabled);
    //     this.actions.get('export').setEnabled(enabled);
    //   }));
    // }

    this.actions.get('new').setEnabled(false);
    this.actions.get('open').setEnabled(false);
    this.actions.get('import').setEnabled(false);
    this.actions.get('save').setEnabled(false);
    this.actions.get('saveAs').setEnabled(false);
    this.actions.get('export').setEnabled(false);
    this.actions.get('print').setEnabled(false);

    // Checks if the browser is supported
    var fileSupport = window.File != null && window.FileReader != null && window.FileList != null;
    var graph = this.editor.graph;
    if (!fileSupport || !mxClient.isBrowserSupported()) {
      // Displays an error message if the browser is not supported.
      mxUtils.error('Browser is not supported!', 200, false);
    } else {
      mxEvent.addListener(container, 'dragover', function(evt)
      {
        if (graph.isEnabled())
        {
          evt.stopPropagation();
          evt.preventDefault();
        }
      });
      
      mxEvent.addListener(container, 'drop', function(evt)
      {
        if (graph.isEnabled())
        {
          evt.stopPropagation();
          evt.preventDefault();

          // Gets drop location point for vertex
          var pt = mxUtils.convertPoint(graph.container, mxEvent.getClientX(evt), mxEvent.getClientY(evt));
          var tr = graph.view.translate;
          var scale = graph.view.scale;
          var x = pt.x / scale - tr.x;
          var y = pt.y / scale - tr.y;
          
          // Converts local images to data urls
          var filesArray = event.dataTransfer.files;
          
          for (var i = 0; i < filesArray.length; i++)
          {
            handleDrop(instance, graph, filesArray[i], x + i * 10, y + i * 10);
          }
        }
      });
    }
  };
  
  // Adds required resources (disables loading of fallback properties, this can only
  // be used if we know that all keys are defined in the language specific file)
  mxResources.loadDefaultBundle = false;
  var bundle = mxResources.getDefaultBundle('mxgraph/resources/grapheditor', mxLanguage) ||
    mxResources.getSpecialBundle('mxgraph/resources/grapheditor', mxLanguage);

  // Fixes possible asynchronous requests
  mxUtils.getAll([bundle, 'mxgraph/styles/default.xml'], function(xhr)
  {
    // Adds bundle text to resources
    mxResources.parse(xhr[0].getText());
    
    // Configures the default graph theme
    var themes = new Object();
    themes[Graph.prototype.defaultThemeName] = xhr[1].getDocumentElement(); 
    
    // Main
    instance.editor = new Editor(urlParams['chrome'] == '0', themes);
    instance.editorUi = new EditorUi(instance.editor, container);
    editorPromiseResolve(instance.editor);
    editorUiPromiseResolve(instance.editorUi);
    instance.contentRaw = null;
  }, function()
  {
    container.innerHTML = '<center style="margin-top:10%;">Error loading resource files. Please check browser console.</center>';
  });

  instance.cache = {};
  instance.notInGraph = {};
  return instance;
};

function round00(num) {
  return Math.round((num + Number.EPSILON) * 100) / 100;
}

// cache from hash to Promise<content>
// moved to instance since there can be multiple (three) editors
// var instance.cache = {};

// vertices which have been added by setContent but haven't
// shown up in the graph yet (waiting for AJAX to load)
// var instance.notInGraph = {};

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

function mxInterfaceGetContent(instance) {
  var editor = instance.editor;
  var json = {};
  json.format_version = 0;
  json.vs = [];
  if (editor != null) {
    var graph = editor.graph;
    var parent = graph.getDefaultParent();
    var vertices = graph.getChildVertices(parent);
    // geometry: mxGeometry {x: 260, y: 190, width: 200, height: 200, relative: false, ...}
    // style: "shape=image;..."
    for (var i = 0; i < vertices.length; i++) {
      var v = vertices[i];
      json.vs.push([
        round00(v.geometry.x),
        round00(v.geometry.y),
        round00(v.geometry.width),
        round00(v.geometry.height),
        hex_sha1(v.style)
      ]);
    }
  }

  Object.keys(instance.notInGraph).forEach(function (key) {
    if (key !== 'uuid') {
      json.vs.push(instance.notInGraph[key]);
    }
  });

  var buffer = '';
  buffer += '{\n'
  buffer += '  "format_version": ' + json.format_version + ',\n'
  buffer += '  "vs": [\n'
  for (var i = 0; i < json.vs.length; i++) {
    buffer += '    ' + JSON.stringify(json.vs[i]);
    if ((i + 1) < json.vs.length) {
      buffer += ',';
    }
    buffer += '\n';
  }
  buffer += '  ]\n'
  buffer += '}\n'
  return buffer;
};

async function mxInterfaceSetContent(instance, content) {
  instance.contentRaw = content;
  var json = null;
  try {
    json = JSON.parse(content);
  } catch (e) {
    console.log('Error while parsing JSON');
    console.log(e);
    console.log(json);
  }
  if (json == null || json.format_version != 0) {
    return;
  }

  var editor = await instance.editorPromise;
  var graph = editor.graph;
  graph.removeCells(graph.getChildVertices(graph.getDefaultParent()));

  var thisContentUUID = uuidv4();
  instance.notInGraph = {};  
  instance.notInGraph['uuid'] = thisContentUUID;
  for (var i = 0; i < json.vs.length; i++) {
    instance.notInGraph[i] = json.vs[i];
  }
  json.vs.forEach(function (v, i) {
    // x, y, w, h, style_sha1
    var hash = v[4];
    if (typeof instance.cache[hash] === 'undefined') {
      instance.cache[hash] = $.ajax({
        type: 'GET',
        data: {
          hash: hash
        },
        url: '/blob',
        dataType : 'text'
      });
    }

    instance.cache[hash].then(function (style) {
      if (instance.notInGraph['uuid'] === thisContentUUID) {
        delete instance.notInGraph[i];
        graph.insertVertex(null, null, '', v[0], v[1], v[2], v[3], style);
      }
    });
  });
};

// main difference from graph.insertVertex is that this assumes
// the style of the vertex is new and will upload it
function insertNewVertex(instance, x, y, w, h, style) {
  var editor = instance.editor;
  if (editor != null) {
    var graph = editor.graph;
    graph.insertVertex(null, null, '', x, y, w, h, style);

    // upload the style as a blob
    var formData = new FormData();
    formData.append('files', new File([new Blob([style])], 'file'));
    $.ajax({
      type: 'POST',
      processData: false, // important
      contentType: false, // important
      data: formData,
      url: '/blob-upload',
      dataType : 'text',
      success: function(text) {
        // console.log(text);
      }
    });
  }
};

// Handles each file as a separate insert for simplicity.
// Use barrier to handle multiple files as a single insert.
function handleDrop(instance, graph, file, x, y)
{
  if (file.type.substring(0, 5) == 'image')
  {
    var reader = new FileReader();

    reader.onload = function(e)
    {
      // Gets size of image for vertex
      var data = e.target.result;

      // SVG needs special handling to add viewbox if missing and
      // find initial size from SVG attributes (only for IE11)
      if (file.type.substring(0, 9) == 'image/svg')
      {
        var comma = data.indexOf(',');
        var svgText = atob(data.substring(comma + 1));
        var root = mxUtils.parseXml(svgText);

        // Parses SVG to find width and height
        if (root != null)
        {
          var svgs = root.getElementsByTagName('svg');
          
          if (svgs.length > 0)
          {
            var svgRoot = svgs[0];
            var w = parseFloat(svgRoot.getAttribute('width'));
            var h = parseFloat(svgRoot.getAttribute('height'));

            // Check if viewBox attribute already exists
            var vb = svgRoot.getAttribute('viewBox');

            if (vb == null || vb.length == 0)
            {
              svgRoot.setAttribute('viewBox', '0 0 ' + w + ' ' + h);
            }
            // Uses width and height from viewbox for
            // missing width and height attributes
            else if (isNaN(w) || isNaN(h))
            {
              var tokens = vb.split(' ');

              if (tokens.length > 3)
              {
                w = parseFloat(tokens[2]);
                h = parseFloat(tokens[3]);
              }
            }

            w = Math.max(1, Math.round(w));
            h = Math.max(1, Math.round(h));

            data = 'data:image/svg+xml,' + btoa(mxUtils.getXml(svgs[0], '\n'));
            var style = 'shape=image;image=' + data + ';';
            insertNewVertex(instance, x, y, w, h, style);
          }
        }
      }
      else
      {
        var img = new Image();

        img.onload = function()
        {
          var w = Math.max(1, img.width);
          var h = Math.max(1, img.height);

          // Converts format of data url to cell style value for use in vertex
          var semi = data.indexOf(';');

          if (semi > 0)
          {
            data = data.substring(0, semi) + data.substring(data.indexOf(',', semi + 1));
          }

          var style = 'shape=image;image=' + data + ';';
          insertNewVertex(instance, x, y, w, h, style);
        };

        img.src = data;
      }
    };

    reader.readAsDataURL(file);
  }
};
