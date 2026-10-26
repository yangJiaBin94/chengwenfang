import React from "react";
import { createRoot } from "react-dom/client";
import { App } from "./app/App";
import "./styles.css";

// Mount the workbench once; StrictMode keeps development lifecycle checks enabled.
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
