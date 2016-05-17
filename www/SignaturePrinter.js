window.initPrinter = function(printer, callback) {
    cordova.exec(callback, function(err) {
        console.log("Error while initPrinter: "+err);
    }, "SignaturePrinter", "initPrinter", [printer]);
};
window.printSignature = function(image,width,height, callback) {
    cordova.exec(callback, function(err) {
        console.log("Error while printSignature: "+err);
    }, "SignaturePrinter", "printSignature", [image,width,height]);
};
window.printText = function(message, callback) {
    cordova.exec(callback, function(err) {
        console.log("Error while printText: "+err);
    }, "SignaturePrinter", "printText", [message]);
};
