package com.morawski.dev.aidevs.tasks.task04sendit;

import java.time.LocalDate;

/**
 * Resolved field values for the SPK "deklaracja zawartości" (S01E04 / {@code sendit}).
 *
 * <p>All values are derived from the SPK documentation rooted at
 * {@code https://hub.ag3nts.org/dane/doc/index.md} and its attachments. The two genuine
 * unknowns from the task brief were resolved as follows:
 *
 * <ul>
 *   <li><b>KATEGORIA = A</b> (Strategiczna). The shipment is "kasety z paliwem do reaktora"
 *       (reactor fuel ≈ "ogniwa paliwowe", listed under kategoria A). Crucially §8.3 states that
 *       closed routes to Żarnowiec "mogą zostać wykorzystane jedynie przy realizacji przesyłek
 *       kategorii A oraz B", and §9.4 ("Przesyłki kat. A i B są zwolnione z opłat") makes A cost
 *       0 PP — matching the 0 PP budget. So the 0-budget constraint forces kategoria A (or B);
 *       the strategic content picks A.</li>
 *   <li><b>TRASA = X-01</b>. From {@code trasy-wylaczone.png} (the excluded-routes table in §8.2):
 *       the Gdańsk – Żarnowiec segment is coded X-01. The simplified map ({@code zalacznik-F.md})
 *       confirms ŻARNOWIEC ===X=== GDAŃSK is a single (closed) hop.</li>
 * </ul>
 *
 * <p>Derived numerics:
 * <ul>
 *   <li><b>WDP = 4</b> (Wagony Dodatkowe Płatne). A standard train carries 2×500 kg = 1000 kg
 *       ({@code dodatkowe-wagony.md}). 2800 kg needs ceil(2800/500) = 6 wagons, i.e. 4 extra.
 *       For strategic shipments the per-wagon fee is waived, so the count is 4 but adds 0 PP.</li>
 *   <li><b>KWOTA DO ZAPŁATY = 0 PP</b> — kategoria A is fully fee-exempt (§9.4).</li>
 * </ul>
 *
 * <p>UWAGI SPECJALNE is intentionally left empty: the brief says to add no special remarks
 * (any remark would trigger manual verification).
 */
record Shipment(
        LocalDate date,
        String punktNadawczy,
        String nadawca,
        String punktDocelowy,
        String trasa,
        String kategoria,
        String opisZawartosci,
        int masaKg,
        int wdp,
        String uwagiSpecjalne,
        String kwotaDoZaplaty
) {
    static Shipment forTask() {
        return new Shipment(
                LocalDate.now(),
                "Gdańsk",
                "450202122",
                "Żarnowiec",
                "X-01",
                "A",
                "kasety z paliwem do reaktora",
                2800,
                4,
                "",          // brak uwag specjalnych — patrz javadoc
                "0 PP");
    }
}
