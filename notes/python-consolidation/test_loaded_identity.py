from loaded_identity import file_origin, origins


def test_only_actual_relevant_load_lines_are_admitted():
    rows = origins('''POM declares org.geotools:gt-api:35.2
[1s][info][class,load] org.geotools.api.X source: file:/C:/repo/gt-api-34.4.jar
[2s][info][class,load] java.lang.String source: shared objects file
[3s][info][class,load] org.geotools.api.X source: file:/C:/repo/gt-api-34.4.jar
[4s][info][class,load] org.h2.Driver source: file:/C:/repo/h2.jar''')
    assert rows == {'file:/C:/repo/gt-api-34.4.jar': {'org.geotools.api.X'},
                    'file:/C:/repo/h2.jar': {'org.h2.Driver'}}


def test_file_origins_decode_windows_urls_without_admitting_network_or_dynamic_origins():
    assert str(file_origin('file:/C:/repo/a%20b.jar')).replace('\\', '/') == 'C:/repo/a b.jar'
    assert file_origin('file://remote/share/plugin.jar') is None
    assert file_origin('__JVM_LookupDefineClass__') is None
