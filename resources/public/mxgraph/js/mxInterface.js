var img = "shape=image;image=data:image/png,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;"

function mxInterfaceInit(container) {
  var editorUiInit = EditorUi.prototype.init;
  var instance = {};

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
    instance.contentRaw = null;
  }, function()
  {
    container.innerHTML = '<center style="margin-top:10%;">Error loading resource files. Please check browser console.</center>';
  });

  return instance;
};

function round00(num) {
  return Math.round((num + Number.EPSILON) * 100) / 100;
}

function mxInterfaceGetContent(instance) {
  var editor = instance.editor;
  var json = {};
  json.version = 0;
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
  console.log(json);
  return instance.contentRaw;
};

function mxInterfaceSetContent(instance, content) {
  var editor = instance.editor;
  if (editor != null) {
    var graph = editor.graph;
    graph.removeCells(graph.getChildVertices(graph.getDefaultParent()));
    for (var i = 0; i < 100; i++) {
      var x = Math.random() * 1000;
      var y = Math.random() * 1000;
      var w = 100;
      var h = 100;
      graph.insertVertex(null, null, '', x, y, w, h, img);
    }
  }
  instance.contentRaw = content;
};

function insertVertex(instance, x, y, w, h, style) {
  graph.insertVertex(null, null, '', x, y, w, h, 'shape=image;image=' + data + ';');
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
            insertVertex(instance, x, y, w, h, style);
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
          insertVertex(instance, x, y, w, h, style);
        };

        img.src = data;
      }
    };
    
    reader.readAsDataURL(file);
  }
};
