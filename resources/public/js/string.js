// https://weblog.rogueamoeba.com/2017/02/27/javascript-correctly-converting-a-byte-array-to-a-utf-8-string/
function stringFromUTF8Array(data)
{
    const extraByteMap = [ 1, 1, 1, 1, 2, 2, 3, 0 ];
    var count = data.length;
    var str = "";

    for (var index = 0;index < count;)
    {
        var ch = data[index++];
        if (ch & 0x80)
        {
            var extra = extraByteMap[(ch >> 3) & 0x07];
            if (!(ch & 0x40) || !extra || ((index + extra) > count))
                return null;

            ch = ch & (0x3F >> extra);
            for (;extra > 0;extra -= 1)
            {
                var chx = data[index++];
                if ((chx & 0xC0) != 0x80)
                    return null;

                ch = (ch << 6) | (chx & 0x3F);
            }
        }

        str += String.fromCharCode(ch);
    }

    return str;
}

// only accepts \n
function splitLines(string) {
    var array = [];
    var buf = "";
    for (var i = 0; i < string.length; i++) {
        var char = string[i];
        if (char !== '\n') {
            buf += char;
        } else {
            array.push(buf);
            buf = "";
        }
    }
    array.push(buf);
    return array;
}
