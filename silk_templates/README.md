This is the base template that site documentation is generated from.

To override, create a `silk_templates` directory in either:
* the same directory as your codex
* the directory that you ran the Protean command from

Only include the sub directories and files that you have made changes to as Protean will fall back to the base files. For example if you're only wanting styling changes then just `silk_templates/resource/css/style.css` is needed.

Please refer to the [Silk Web Toolkit's Documentation Page](http://www.silkyweb.org/documentation.html) for more details on:
* Silks expected directory structure
* Templates - you may wish to brand your API docs by making changes here
* Views - you may wish to add more or change page layout
* Components
* Sources - the Protean Codex is converted into a Silk Source and injected using Silk's data binding semantics
* View Nav - Protean uses this in the sites header navigation (meaning additional views will be picked up automatically)
* Bookmark Nav - used to create the side menu
* Tipue Search Support - searchable page contents
