window.addEventListener("message", (event) => {
  if (event.source !== window || event.data.type !== "TLABWEBVIWE_GECKO_NATIVE_MESSAGE") {
    return;
  }
  browser.runtime.sendNativeMessage("browser", event.data.payload);
});