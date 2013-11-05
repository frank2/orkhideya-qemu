orkhideya-qemu
==============

basic VM configuration scripts for varying operating systems!

for use with ork.qemu, found in orkhideya-modules!

to create a qemu machine with this configuration, write your own scripts. for
example, I want to create an x86_64-based Windows 7 machine. my script would
look something like this:

   #!/bin/bash

   source orkhideya
   ork_include qemu

   source "$(qemu_vm_boot_file arch.x86-64)"
   source "$(qemu_vm_boot_file os.windows.7)"
   qemu_vm_boot machine "x86_64-windows-7"
