# SignaturePrinter
  Simple plugin for Apache Cordova for printing text and images via Bluetooth POS Printers
# Installation
  cordova plugin add SignaturePrinter
# Usage
  document.addEventListener("deviceready", onDeviceReady, false);
  function onDeviceReady() {
  	window.initPrinter(printerMacAddress,successCallback);
  }
  
  window.printText(stringToPrint,successCallback);  
  
  window.printSignature(imageColors,width,height,successCallback);//imageColors is int array of pixels(ARGB)
