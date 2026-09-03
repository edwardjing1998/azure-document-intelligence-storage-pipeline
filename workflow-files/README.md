# Visible workflow copies

macOS Finder normally hides `.github` because its name starts with a period.

This directory contains visible reference copies of the workflow YAML files. GitHub does **not** execute files from this directory. The canonical executable files are:

```text
.github/workflows/ci.yml
.github/workflows/process-storage-documents.yml
.github/workflows/deploy-openshift.yml
```

`workflow-files/deploy-openshift.yml` is a visible pointer to the active OpenShift workflow.

If you edit a visible copy, apply the same change to the corresponding canonical file under `.github/workflows/`.

To display hidden files in macOS Finder, press:

```text
Command + Shift + .
```
