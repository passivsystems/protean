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
mkdir -p %{buildroot}/usr/share/protean
install -m644 target/*standalone* %{buildroot}/usr/lib/protean/protean.jar
install -m644 defaults.edn %{buildroot}/usr/lib/protean/defaults.edn
cp -r public/ %{buildroot}/usr/lib/protean/public/
rm -rf silk_templates/data/protean-api/*
rm -rf silk_templates/site/*
cp -r silk_templates/ %{buildroot}/usr/lib/protean/silk_templates/
install -m755 build/etc/protean %{buildroot}/usr/bin

%files
%defattr (-,root,root,-)
/usr/

%post


%preun
