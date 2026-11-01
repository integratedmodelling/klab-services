/* Offline expression-equivalence probe, not k.LAB execution or a Puerto port.
 * Evaluates only the first two expressions from the checksum-pinned PaL archive.
 * Expected numbers are separately specified; derivative expressions must match.
 */
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.security.MessageDigest
import java.util.zip.ZipFile

if (args.length != 3) throw new IllegalArgumentException('Supply original PaL ZIP, adapted ZIP and known-answer JSON')
def original = new File(args[0])
if (original.length() != 4704395L) throw new IllegalArgumentException('Original archive size differs')
def digest = MessageDigest.getInstance('SHA-256').digest(original.bytes).encodeHex().toString()
if (digest != '6503feeb0dc743601fe3e3205c879354801fc21353913fd06b9ae4ff1443d872') {
    throw new IllegalArgumentException('Original archive pin differs')
}
def expressions = { String zipPath ->
    new ZipFile(zipPath).withCloseable { zip ->
        def entry = zip.entries().find { it.name.endsWith('/lf/radiation.kim') }
        if (entry == null || entry.size > 16384) throw new IllegalArgumentException('Missing or oversized Radiation source')
        def text = zip.getInputStream(entry).withCloseable { it.getText('UTF-8') }
        (text =~ /set to \[([^\]]*)\]/).collect { it[1] }.take(2)
    }
}
def before = expressions(args[0])
def after = expressions(args[1])
if (before.size() != 2 || before != after) throw new IllegalArgumentException('Original and adapted equations differ')
def oracle = new JsonSlurper().parse(new File(args[2]))
if (oracle.cases.size() != 8 || oracle.basis.threshold != 25
    || oracle.absolute_arithmetic_tolerance != 1e-12
    || oracle.cases.collect { it.id }.unique().size() != 8) throw new IllegalArgumentException('Unexpected oracle')
def check = { List equations ->
    oracle.cases.collect { item ->
        def bindings = new Binding([rg: item.rg.doubleValue(), com: 'main',
            com2: item.same_class ? 'main' : 'overstory', rgc_max: 25d])
        def shell = new GroovyShell(bindings)
        def rg2 = shell.evaluate(equations[0])
        bindings.setVariable('rg2', rg2)
        def fr = shell.evaluate(equations[1])
        if (!Double.isFinite(rg2.doubleValue()) || !Double.isFinite(fr.doubleValue())
            || Math.abs(rg2.doubleValue() - item.expected_rg2.doubleValue()) > oracle.absolute_arithmetic_tolerance
            || Math.abs(fr.doubleValue() - item.expected_fr.doubleValue()) > oracle.absolute_arithmetic_tolerance) {
            throw new IllegalStateException('Known-answer mismatch: ' + item.id)
        }
        [id: item.id, rg2: rg2, fr: fr]
    }
}
def originalValues = check(before)
def adaptedValues = check(after)
def rejected = []
['attenuation': [before[0].replace('rg * 0.2', 'rg * 0.3'), before[1]],
 'saturation': [before[0], before[1].replace('? 1 :', '? 0 :')]].each { name, mutant ->
    if (mutant == before) throw new IllegalStateException('Mutation did not change the probe')
    try { check(mutant) }
    catch (IllegalStateException expected) { rejected.add(name) }
}
if (rejected.size() != 2) throw new IllegalStateException('Oracle accepted a deliberately wrong equation')
println JsonOutput.toJson([status: 'PASS_EXPRESSION_EQUIVALENCE_ONLY', original_cases: 8,
    adapted_cases: 8, original_values: originalValues, adapted_values: adaptedValues,
    groovy_version: GroovySystem.version, rejected_mutations: rejected, scientific_acceptance: false,
    live_services: 'NOT_RUN', limits: oracle.not_established])
