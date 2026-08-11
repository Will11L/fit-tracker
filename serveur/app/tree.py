#!/usr/bin/env python3
import os
import argparse

def print_tree(root: str, prefix: str = "", exclude=None, max_depth=None, level=0, show_files=True):
    if exclude is None:
        exclude = {"node_modules", ".git", ".angular", "__pycache__"}

    if max_depth is not None and level > max_depth:
        return

    try:
        entries = sorted(os.listdir(root))
    except PermissionError:
        return

    for i, entry in enumerate(entries):
        if entry in exclude:
            continue
        path = os.path.join(root, entry)
        connector = "└── " if i == len(entries) - 1 else "├── "
        if os.path.isdir(path):
            print(prefix + connector + entry + "/")
            extension = "    " if i == len(entries) - 1 else "│   "
            print_tree(path, prefix + extension, exclude, max_depth, level + 1, show_files)
        else:
            if show_files:
                print(prefix + connector + entry)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Afficher la structure d'un projet en arbre.")
    parser.add_argument("root", nargs="?", default=".", help="Répertoire racine (par défaut: .)")
    parser.add_argument("--max-depth", type=int, help="Profondeur maximale à parcourir")
    parser.add_argument("--no-files", action="store_true", help="Ne pas afficher les fichiers")
    parser.add_argument("--exclude", nargs="*", help="Répertoires à exclure en plus des défauts")
    args = parser.parse_args()

    exclude = {"node_modules", ".git", ".angular", "__pycache__"}
    if args.exclude:
        exclude.update(args.exclude)

    print(args.root)
    print_tree(args.root, exclude=exclude, max_depth=args.max_depth, show_files=not args.no_files)
