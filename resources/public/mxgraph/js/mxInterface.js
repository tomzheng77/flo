
function mxInterfaceInit(container) {
  var editorUiInit = EditorUi.prototype.init;

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
            handleDrop(graph, filesArray[i], x + i * 10, y + i * 10);
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
    new EditorUi(new Editor(urlParams['chrome'] == '0', themes), container);
  }, function()
  {
    container.innerHTML = '<center style="margin-top:10%;">Error loading resource files. Please check browser console.</center>';
  });

  return {}; // will later be passed as instance
};

function mxInterfaceGetContent(instance) {
  return instance.contentRaw;
};

function mxInterfaceSetContent(instance, content) {
  instance.contentRaw = content;
};

// Handles each file as a separate insert for simplicity.
// Use barrier to handle multiple files as a single insert.
function handleDrop(graph, file, x, y)
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
            graph.insertVertex(null, null, '', x, y, w, h, 'shape=image;image=' + data + ';');
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

          graph.insertVertex(null, null, '', x, y, w, h, 'shape=image;image=' + data + ';');
        };
        
        img.src = data;
      }
    };
    
    reader.readAsDataURL(file);
  }
};
