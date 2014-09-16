%define __jar_repack 0
%define __os_install_post %{nil}
Name: protean
Version: %{version}
Release: %{release}
Group: System Environment/Daemons
Summary: Protean API simulation, documentation, testing and negotiation
Vendor: github.com/passivsystems
License: Apache License v 2.0
Buildroot: %{_topdir}/BUILDROOT/%{name}-%{version}.%{_arch}

%description
Protean API simulation, documentation, testing and negotiation.

%build


%install
cd %{_topdir}/../
mkdir -p %{buildroot}/usr/bin
mkdir -p %{buildroot}/usr/lib/protean
cp -r target/*standalone* %{buildroot}/usr/lib/protean/protean.jar
cp -r public %{buildroot}/usr/lib/protean
cp -r build/etc/protean-server %{buildroot}/usr/bin
cp -r build/etc/protean %{buildroot}/usr/bin

%files
%defattr (755,root,root,-)
/usr/bin/protean
/usr/lib/protean/protean.jar

%post


%preun
