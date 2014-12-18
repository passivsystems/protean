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
install -m644 sample-petstore.cod.edn %{buildroot}/usr/lib/protean/sample-petstore.cod.edn
install -m644 sample-petstore.sim.edn %{buildroot}/usr/lib/protean/sample-petstore.sim.edn
install -m644 protean-utils.cod.edn %{buildroot}/usr/lib/protean/protean-utils.cod.edn
install -m644 protean-utils.cod.edn %{buildroot}/usr/lib/protean/protean-utils.cod.edn
cp -r public/ %{buildroot}/usr/lib/protean/public/
cp -r silk_templates/ %{buildroot}/usr/lib/protean/silk_templates/
cp -r test-data/ %{buildroot}/usr/lib/protean/test-data/
install -m755 build/etc/protean-server %{buildroot}/usr/bin
install -m755 build/etc/protean %{buildroot}/usr/bin

%files
%defattr (-,root,root,-)
/usr/

%post


%preun
