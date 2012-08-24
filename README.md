# Pallet crate for forever

This a crate to install and run forever via
[Pallet](http://pallet.github.com/pallet).

[Release Notes](https://github.com/pallet/forever-crate/blob/master/ReleaseNotes.md)

## Server Spec

The forever crate defines the `forever` function, that takes a settings map and
returns a server-spec for installing forever.

## Settings

The `forever-settings` function takes the following settings:

:version
the version to install

The default install is via `npm`. node.js needs to be installed on the machine
before this crate will function.

## Running services under forever

The `forever-service` function can be used to start and stop processes under
forever's supervision.

## Support

[On the group](http://groups.google.com/group/pallet-clj), or
[#pallet](http://webchat.freenode.net/?channels=#pallet) on freenode irc.

## License

Licensed under [EPL](http://www.eclipse.org/legal/epl-v10.html)

Copyright 2012 Hugo Duncan.
