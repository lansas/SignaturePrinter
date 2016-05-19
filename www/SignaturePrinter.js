window.initPrinter = function(printer, callbackSuccess,callbackError) {
    cordova.exec(callbackSuccess,callbackError, "SignaturePrinter", "initPrinter", [printer]);
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
