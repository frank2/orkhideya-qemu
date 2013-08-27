#!/bin/bash 

source orkhideya
ork_include proc
ork_include if
ork_include mac
ork_include qemu
ork_include stdout

_qemu_arch="$1"

if [ -z "$_qemu_arch" ]; then
   stdout_warning "No arch set; defaulting to $(stdout_color_wrap main-focused "x86_64")."
   _qemu_arch="x86_64"
fi

if [ -z "$(which qemu-system-$_qemu_arch 2>/dev/null)" ]; then
   stdout_error "qemu arch $(stdout_color_wrap main-focused "$_qemu_arch") unsupported."
   exit 2
fi

_label="$2"
if [ -z "$_label" -o "$_label" == '-' ]; then
   _label="qemu-machine"
fi

if proc_label_is_running "$_label"; then
   stdout_warning "qemu machine $(stdout_color_wrap main-focused "$_label") already appears to be running."
   _kill="$(stdout_prompt_yesno "Terminate it?")"

   if [ "$_kill" == "y" ]; then
      proc_label_kill "$_label"
   else
      stdout_error "Not spawning machine on account of doppelganger."
      exit 3
   fi
fi

_args="${@:3}"

stdout_normal "Building base commandline for $(stdout_color_wrap main-focused "qemu") machine."

_args=""$_args" "$(qemu_arg_default "$_args" enable-kvm)""
_args=""$_args" "$(qemu_arg_default "$_args" soundhw hda)""
_args=""$_args" "$(qemu_arg_default "$_args" name qemuvm)""
_args=""$_args" "$(qemu_arg_default "$_args" "net nic" ",model=e1000,name=eth0,macaddr=$(mac_random)")""
#_args=""$_args" "$(qemu_arg_default "$_args" "device usb-ehci" ",id=usb2")""
_args=""$_args" "$(qemu_arg_default "$_args" vga std)""

if ! qemu_arg_exists "$_args" monitor; then
   stdout_normal "No monitor provided. Looking for port to host it on."

   for _port in $(seq 4000 5000); do 
      if [ -z "$(lsof -n -i :$_port | grep -o '\(LISTEN\)')" ]; then break; fi
   done

   stdout_normal "Machine monitor will be hosted on port $(stdout_color_wrap main-focused "$_port")."

   _args=""$_args" -monitor tcp:$ORK_QEMU_VM_HOST:$_port,server,nowait"
fi

if ! qemu_arg_exists "$_args" vnc; then
   stdout_normal "No VNC provided. Looking for port to host it on."

   for _port in $(seq 0 99); do 
      _real_port="59$(printf '%02d' $_port)"
      if [ -z "$(lsof -n -i :$_real_port | grep -o '\(LISTEN\)')" ]; then break; fi
   done

   stdout_normal "VNC will be hosted on port $(stdout_color_wrap main-focused "$ORK_QEMU_VM_HOST:$_real_port")."
   _args=""$_args" -vnc $ORK_QEMU_VM_HOST:$_port"
fi

if ! qemu_arg_exists "$_args" netdev; then
   stdout_warning "No network device provided. Making one instead."

   if [ -z "$(if_interface_list | egrep '^vm-ro[[:digit:]]+$')" ]; then
      stdout_warning "No router exists to latch onto. Making one instead."
      _router_interface="$(stdlib_tmpfile "$_label-router")"

      if_class_create router "$(if_gateway_interface)" vm-ro >"$_router_interface"

      if [ "$?" != "0" ]; then
         shred -u "$_router_interface"
         stdout_warning "Router creation failed."
         exit 2
      fi

      _router="$(cat "$_router_interface")"
      
      function qemu_router_cleanup
      {
         if_class_destroy router "$_router"
      }

      stdlib_trap_cleanup_push qemu_router_cleanup
   else
      _router="$(if_interface_list | egrep -o '^vm-ro[[:digit:]]+$' | sort -r | head -n1)"
   fi

   stdout_normal "Selected $(stdout_color_wrap main-focused "$_router") as VM router."
   stdout_warning "Bringing it up."
   stdlib_trap orkhideya-elevate if_interface_up "$_router"

   stdout_warning "Creating tunnel to $(stdout_color_wrap main-focused "$_router")."
   _tunnel_interface="$(stdlib_tmpfile "$_label-tunnel")"

   if_class_create tunnel "$_router" vm-tun >"$_tunnel_interface"

   if [ "$?" != "0" ]; then
      shred -u "$_tunnel_interface"
      stdout_warning "Tunnel creation failed."
      stdlib_trap [ "0" == "1" ]
   fi

   _tunnel="$(cat "$_tunnel_interface")"

   function qemu_tunnel_cleanup
   {
      if_class_destroy tunnel "$_tunnel"
   }

   stdlib_trap_cleanup_push qemu_tunnel_cleanup

   stdout_normal "Tunnel $(stdout_color_wrap main-focused "$_tunnel") created."
   _args=""$_args" "$(qemu_arg_default "$_args" "net tap" ",ifname=$_tunnel,script=no")""

   stdout_warning "Bringing it up."
   stdlib_trap orkhideya-elevate if_interface_up "$_tunnel"

   stdout_normal "Tunnel is up."
fi

stdout_warning "Launching new qemu machine."
stdlib_trap proc_label_spawn "$_label" "qemu-system-$_qemu_arch $_args"

stdout_normal "qemu machine launched as $(stdout_color_wrap main-focused "$_label")."

exit 0
