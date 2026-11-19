// Case history, kept in this browser (localStorage). A case keeps its full
// image only while a step is still waiting for signal; after that only a
// small thumbnail stays, so storage doesn't fill up.

const KEY = "cropdoctor.cases.v1";
const MAX_CASES = 30;

export function loadCases() {
  try {
    const list = JSON.parse(localStorage.getItem(KEY) || "[]");
    // A case can't still be "running" after a page reload.
    return list.map((c) => (c.status === "running" ? { ...c, status: c.image ? "waiting" : "error", error: c.image ? "" : "Interrupted." } : c));
  } catch {
    return [];
  }
}

export function saveCases(cases) {
  const list = cases.slice(0, MAX_CASES);
  try {
    localStorage.setItem(KEY, JSON.stringify(list));
  } catch {
    // Storage full: keep metadata, drop full images of finished cases.
    try {
      localStorage.setItem(KEY, JSON.stringify(list.map((c) => (c.status === "waiting" ? c : { ...c, image: null }))));
    } catch {
      /* give up silently; the case is still on screen */
    }
  }
}

export function newCaseId() {
  return "CD-" + Date.now().toString(36).toUpperCase().slice(-5);
}

export function makeThumb(dataUrl, max = 160) {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      const s = Math.min(1, max / Math.max(img.naturalWidth, img.naturalHeight));
      const cv = document.createElement("canvas");
      cv.width = Math.round(img.naturalWidth * s);
      cv.height = Math.round(img.naturalHeight * s);
      cv.getContext("2d").drawImage(img, 0, 0, cv.width, cv.height);
      resolve(cv.toDataURL("image/jpeg", 0.7));
    };
    img.onerror = () => resolve("");
    img.src = dataUrl;
  });
}
