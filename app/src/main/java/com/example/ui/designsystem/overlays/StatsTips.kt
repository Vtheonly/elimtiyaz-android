package com.example.ui.designsystem.overlays

/**
 * StatsTips — T-458 (128th session): the Android port of the desktop's
 * T-447 bilingual Statistics tooltip glossary
 * (src/i18n/stats-tips.ts — the glossary of record for every Statistics
 * element's explainability).
 *
 * GENERATED from the desktop's FR tree by scripts/gen_stats_tips_kotlin.mjs
 * (the verbatim-port discipline — regenerate rather than hand-edit; the
 * FR texts are the reference per the desktop's own file header).
 *
 * The entry shape mirrors StatsTipEntry: title + measures + calc
 * (+ status when the element renders a verdict). The texts describe the
 * CANONICAL derivations (the §15.53a rule) — the FR text is the
 * documentation of record.
 *
 * Platform note: the Android app is French-only today (no language
 * switcher — the FR tree alone is ported); the EN/AR trees land with the
 * app's future locale system (a registered follow-up, NOT a glossary
 * gap: the lookup API is the dotted key).
 */
object StatsTips {

    /** One glossary entry (title + measures + calc + optional status). */
    data class Entry(
        val title: String,
        val measures: String,
        val calc: String,
        val status: String? = null,
    )

    /** The section labels (the desktop's _meta — the tooltip's field
     *  headers, dictionary strings, never hardcoded). */
    val metaMeasures: String = "Mesure :"
    val metaCalc: String = "Calcul :"
    val metaStatus: String = "Statut :"

    /** The viewMode section. */
    val viewMode: Map<String, Entry> = mapOf(
        "pilotage" to Entry(
            title = "Vue Pilotage Exécutif",
            measures = "Les déclencheurs opérationnels de la direction : vagues de tranches, triage des créances, concentration du risque familial.",
            calc = "Chaque carte dérive les mêmes flux de données de référence que Finances (installments, payments, ledger) via les moteurs canoniques du domaine.",
            status = "L'onglet actif est surligné ; les autres vues restent accessibles sans rechargement."
        ),
        "diagnostic" to Entry(
            title = "Vue Diagnostic Actif",
            measures = "L'exploration par élève : profils de risque croisés (pédagogique, assiduité, financier) et matrices d'analyse.",
            calc = "Les profils proviennent d'une seule évaluation (evaluateStudentRiskProfiles) partagée par toutes les cartes — jamais recalculée par carte.",
            status = "Une seule évaluation alimente toutes les vues : les compteurs ne peuvent pas diverger entre cartes."
        ),
        "charts" to Entry(
            title = "Vue Flux Financiers",
            measures = "Les statistiques descriptives des encaissements : méthodes, catégories, comparaison annuelle, vieillissement, Pareto.",
            calc = "Tous les agrégats partent de la tranche filtrée (paiements réglés, dans la période, conformes aux slicers) — la même définition que les KPI.",
            status = "Les filtres actifs (mode/pôle) recalculent instantanément chaque carte de cette vue."
        ),
    )

    /** The header section. */
    val header: Map<String, Entry> = mapOf(
        "academicYear" to Entry(
            title = "Année active",
            measures = "L'année scolaire de référence de toutes les statistiques de cette page.",
            calc = "Les tranches sont filtrées par fenêtre de facturation (1er sept → 1er sept suivant) — le même périmètre que l'onglet Finances → Tranches.",
            status = "Changer d'année dans le sélecteur recalcule chaque métrique dérivée des tranches."
        ),
    )

    /** The slicers section. */
    val slicers: Map<String, Entry> = mapOf(
        "header" to Entry(
            title = "Filtres Dynamiques (Slicers)",
            measures = "Le filtrage croisé de toutes les cartes de paiements de la vue Flux Financiers (sémantique Power BI).",
            calc = "Un slicer actif restreint la tranche aux paiements réglés correspondants ; ensemble vide = aucun filtre (tout inclus).",
            status = "Le badge affiché compte les opérations retenues / total et le montant encaissé filtré."
        ),
        "methods" to Entry(
            title = "Slicers Mode de paiement",
            measures = "Les méthodes d'encaissement : espèces, chèque, virement.",
            calc = "Le filtrage s'applique au champ payments.method de la tranche déjà restreinte aux paiements réglés dans la période.",
            status = "Un chip surligné = méthode incluse ; plusieurs chips actives s'additionnent (OU logique)."
        ),
        "categories" to Entry(
            title = "Slicers Pôle / Catégorie",
            measures = "Les catégories de facturation (scolarité, transport, cantine, uniforme, livres, psychologie, orthophonie, activités…).",
            calc = "Le filtrage s'applique au champ payments.category ; « Multi-services » (ADR-023) est un chip distinct pour les encaissements globaux.",
            status = "Ensemble vide = toutes catégories ; le chip actif filtre, le chip inactif exclut."
        ),
        "badge" to Entry(
            title = "Badge d'effectif filtré",
            measures = "Le compte d'opérations retenues sur le total, et le total encaissé de la tranche filtrée.",
            calc = "N paiements retenus / N total (hors filtres) · Σ des montants de la tranche filtrée.",
            status = "Si le compte filtré est inférieur au total, des slicers actifs excluent des opérations."
        ),
        "reset" to Entry(
            title = "Réinitialiser les filtres",
            measures = "La remise à zéro de tous les slicers (méthodes et catégories).",
            calc = "Remet l'état des filtres à l'ensemble vide — toutes les opérations réglées de la période re-deviennent incluses.",
            status = "Le bouton n'apparaît que lorsqu'au moins un slicer est actif."
        ),
    )

    /** The statStrip section. */
    val statStrip: Map<String, Entry> = mapOf(
        "total" to Entry(
            title = "Total Encaissé",
            measures = "La somme des encaissements de la tranche filtrée (paiements réglés).",
            calc = "Σ payment.amount sur la tranche — la même définition que la KPI « Encaissé » du tableau de bord."
        ),
        "count" to Entry(
            title = "Volume Transactions",
            measures = "Le nombre d'opérations (versements) de la tranche filtrée.",
            calc = "Compte des paiements de la tranche ; les chèques/virements non compensés sont exclus (statut ≠ réglé)."
        ),
        "mean" to Entry(
            title = "Panier Moyen",
            measures = "Le montant moyen par opération encaissée.",
            calc = "Σ montants ÷ nombre d'opérations, arrondi au dinar (moyenne arithmétique)."
        ),
        "median" to Entry(
            title = "Médiane",
            measures = "Le montant central de la distribution des encaissements (50 % au-dessus, 50 % en-dessous).",
            calc = "Tri des montants ; valeur centrale, ou moyenne des deux centraux si le compte est pair."
        ),
        "bestMonth" to Entry(
            title = "Mois Record",
            measures = "Le mois calendaire avec le plus fort encaissement de la tranche.",
            calc = "Agrégation par mois calendaire de collectedAt ; le mois au Σ le plus élevé (étiquette + montant)."
        ),
        "stdDev" to Entry(
            title = "Volatilité (σ)",
            measures = "La dispersion des montants autour du panier moyen.",
            calc = "Écart-type d'échantillon : √( Σ(x − moyenne)² ÷ (n − 1) ), arrondi au dinar. σ faible = montants homogènes (tranches fixes)."
        ),
    )

    /** The waveVelocity section. */
    val waveVelocity: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Vélocité de Recouvrement par Vague",
            measures = "L'analyse T1/T2/T3 TOUTES CATÉGORIES : ce qui a été facturé, encaissé, en cours et reste dû par vague saisonnière.",
            calc = "Chaque carte est le POOL canonique (domain/calc/payment/tranche-waves) de toutes les catégories de la vague — l'EXACT objet que l'onglet Finances → Tranches consomme : parité au dinar près par construction.",
            status = "Clôturée = reste dû nul · En retard = au moins une échéance non soldée dépassée · En cours = le reste."
        ),
        "collectedPct" to Entry(
            title = "Taux de recouvrement",
            measures = "Le pourcentage du montant facturé déjà encaissé (fonds compensés) sur la vague.",
            calc = "round(Σ amountPaid ÷ Σ amountDue × 100) — convention PARITY-001, jamais plafonné (une vague sur-couverte peut dépasser 100 %).",
            status = "Le code couleur suit le statut de la vague, pas le pourcentage."
        ),
        "dossiers" to Entry(
            title = "Dossiers (X/Y)",
            measures = "Les engagements soldés sur le total d'engagements de la vague, toutes catégories.",
            calc = "Comptage du prédicat canonique isInstallmentSettled (INV-4) : statut « payé » OU reste dû nul (les fonds en instance couvrent aussi)."
        ),
        "due" to Entry(
            title = "Facturé",
            measures = "Le total facturé de la vague, toutes catégories confondues.",
            calc = "Σ amountDue des lignes de la vague (arrondi par ligne, domaine DZD entier)."
        ),
        "paid" to Entry(
            title = "Encaissé",
            measures = "Les fonds compensés (espèces, chèques/virements débloqués) appliqués à la vague.",
            calc = "Σ amountPaid des lignes de la vague — les fonds en instance (non compensés) sont comptés dans « En cours »."
        ),
        "pending" to Entry(
            title = "En cours",
            measures = "Les fonds NON compensés posés sur les tranches de la vague (chèques/virements en attente de validation bancaire).",
            calc = "Σ amountPending des lignes de la vague ; la ligne de contrôle de la carte prouve : Facturé = Encaissé + En cours + Reste dû.",
            status = "Ces fonds deviennent « Encaissé » quand le paiement passe réglé, ou sont annulés si le chèque est rejeté."
        ),
        "remaining" to Entry(
            title = "Reste dû",
            measures = "Le solde encore à recouvrer sur la vague.",
            calc = "Σ INV-4 par ligne : max(0, amountDue − amountPaid − amountPending).",
            status = "Rouge quand > 0 ; une vague à reste dû nul est « Clôturée »."
        ),
        "families" to Entry(
            title = "Familles (débitrices / totales)",
            measures = "Les familles qui doivent encore sur la vague, sur le total des familles facturées (union des catégories — jamais de double-compte).",
            calc = "Ensemble-union des familles par vague ; le sous-libellé suit la phase : « en retard » (échéance dépassée), « à échoir » (futur), « non soldées ».",
            status = "Rouge = familles en retard réelles ; orange = familles devant mais pas encore échues."
        ),
        "categories" to Entry(
            title = "Catégories (compteur + chips)",
            measures = "Le nombre de catégories de facturation présentes dans la vague, et leur détail (taux + reste dû par catégorie).",
            calc = "Chips = les lignes canoniques par (catégorie × vague) ; chaque chip : round(encaissé ÷ facturé × 100) et le reste dû compact.",
            status = "La chip rouge signale un reste dû ; les chips prouvent qu'AUCUNE catégorie n'est silencieusement exclue."
        ),
        "identity" to Entry(
            title = "Ligne de contrôle (réconciliation)",
            measures = "La preuve arithmétique de la carte : Total dû = Encaissé + En cours + Reste dû.",
            calc = "Identité exacte par ligne : due + max(0, payé + en instance − dû) = payé + en instance + reste dû. Le terme « couverts au-delà » n'apparaît que si des fonds excèdent le dû (crédit posé sur la ligne)."
        ),
        "echeance" to Entry(
            title = "Échéance (plage)",
            measures = "La plage de dates d'échéance réelle des lignes de la vague (min → max).",
            calc = "Dérivée des dates des lignes elles-mêmes — jamais du calendrier officiel codé en dur ; « N j de retard » compte les jours depuis l'échéance la plus ancienne non soldée.",
            status = "Rouge = au moins une échéance dépassée ; « dans N j » = vague pas encore due."
        ),
        "phase" to Entry(
            title = "Statut de la vague",
            measures = "Le verdict de la vague : Clôturée, En retard, ou En cours.",
            calc = "Clôturée = reste dû nul (INV-4) · En retard = au moins une ligne non soldée à échéance dépassée · En cours = le reste.",
            status = "Vert (clôturée) · Rouge (en retard) · Bleu (en cours). Un verdict sans sa cause visible serait indiscernable d'un bug (UI-316)."
        ),
        "breakdown" to Entry(
            title = "Détail par Catégorie de Facturation",
            measures = "La lecture catégorie par catégorie des vagues (scolarité T1, transport T1, etc.).",
            calc = "Les lignes canoniques par (catégorie × tranche 1..3) — le même regroupement dont le pool principal est la somme.",
            status = "« en retard » = cette catégorie précise porte une échéance dépassée non soldée."
        ),
        "nonWave" to Entry(
            title = "Hors Tranches — FI & engagements non-tranches",
            measures = "Les frais d'inscription (FI) et les engagements hors modèle 3-tranches : « Année complète », échéanciers personnalisés, lignes héritées hors bornes.",
            calc = "Les lignes à tranche 0 (FI), sans numéro ou hors 1..3, regroupées par (type × catégorie) — exclues des vagues PAR CONCEPTION, jamais silencieusement.",
            status = "« soldé » = reste dû nul sur le groupe ; rouge = reste dû ouvert."
        ),
        "globalBadges" to Entry(
            title = "Badges globaux",
            measures = "Le taux de recouvrement global, les fonds en instance et le reste dû total des trois vagues.",
            calc = "Σ sur les trois vagues du pool canonique : round(encaissé ÷ facturé × 100), Σ en instance (badge info), Σ reste dû (badge rouge)."
        ),
    )

    /** The triage section. */
    val triage: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Triage des créances",
            measures = "La répartition de l'encours par ancienneté réelle du retard — le triage d'action (relance, intervention).",
            calc = "Chaque tranche non soldée à reste dû > 0 est classée par jours de retard (floor) avec les SEUILS CONFIGURABLES du suivi des dettes (les mêmes que Finances).",
            status = "Les bornes des libellés suivent la configuration (Paramètres) — jamais un seuil codé en dur local."
        ),
        "notDue" to Entry(
            title = "Non échue",
            measures = "L'encours dont l'échéance n'est pas encore passée (tranches futures — pas une mauvaise dette).",
            calc = "Reste dû INV-4 des lignes à échéance ≥ aujourd'hui.",
            status = "Ce montant explique pourquoi le « Total Dette » brut alarme à tort : il contient l'année entière."
        ),
        "current" to Entry(
            title = "Retard ≤ seuil jaune (à surveiller)",
            measures = "Le retard transitoire (cycle de salaire) sous le seuil jaune configuré.",
            calc = "Reste dû des lignes avec 0 < jours de retard ≤ yellowDays (défaut 15 j).",
            status = "Couche jaune du statut canonique 4 niveaux."
        ),
        "reminder" to Entry(
            title = "Retard jaune→rouge (relance)",
            measures = "Le retard soutenu qui justifie une relance active (WhatsApp).",
            calc = "Reste dû des lignes avec yellowDays < jours de retard ≤ redDays (défaut 60 j).",
            status = "Couche orange du statut canonique."
        ),
        "chronic" to Entry(
            title = "Retard > seuil rouge (intervention)",
            measures = "La dette chronique au-delà du seuil rouge — verrouillage de compte / intervention de la direction.",
            calc = "Reste dû des lignes à jours de retard > redDays.",
            status = "Couche rouge du statut canonique ; alimente la liste d'appel."
        ),
        "callList" to Entry(
            title = "Liste d'appel",
            measures = "Les familles au-delà du rouge, classées par exposition décroissante.",
            calc = "Familles dont la pire échéance dépasse redDays ; montant = leur encours TOTAL (toutes couches), top 10.",
            status = "L'exposition peut dépasser le seul bucket chronique — c'est l'encours complet de la famille."
        ),
    )

    /** The erosion section. */
    val erosion: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Taux d'Érosion des Remises",
            measures = "L'impact des concessions tarifaires négociées sur le potentiel de revenu.",
            calc = "Depuis le grand livre : les ajustements de crédit négatifs (remises) sur la base brute facturée ; l'érosion = round(remises ÷ (charges brutes + remises) × 100)."
        ),
        "count" to Entry(
            title = "Remises (nombre)",
            measures = "Le nombre d'ajustements de remise négociés et les familles concernées.",
            calc = "Comptage des écritures type « ajustement » à montant négatif (contrat d'identification T-389 : metadata.field = REMISE)."
        ),
        "total" to Entry(
            title = "Σ Remises",
            measures = "Le montant total des remises accordées.",
            calc = "Σ |montant| des écritures de remise ; le « net » soustrait les annulations de double-remise (réconciliation 0063)."
        ),
        "net" to Entry(
            title = "Remises nettes",
            measures = "Les remises après annulations (netting des devis importés).",
            calc = "Σ remises − Σ annulations (écritures de débit positives appariées)."
        ),
        "pct" to Entry(
            title = "Taux d'érosion",
            measures = "La part du potentiel brut concédée en remises.",
            calc = "round(remises ÷ sticker total × 100) où sticker = charges brutes + remises (les devis importés sont déjà nets)."
        ),
        "avg" to Entry(
            title = "Remise moyenne / max",
            measures = "La remise typique et la plus forte concession.",
            calc = "Moyenne = Σ remises ÷ nombre ; min/max sur les mêmes écritures."
        ),
    )

    /** The concentration section. */
    val concentration: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Concentration du risque familial",
            measures = "La part de l'encours total portée par le Top 10 des familles (règle 80/20).",
            calc = "Encours par famille = Σ INV-4 sur ses lignes non soldées ; concentration = round(top 10 ÷ total × 100).",
            status = "Une concentration élevée = le non-recouvrement de quelques familles suffirait à creuser le trou."
        ),
        "top" to Entry(
            title = "Top 10 des familles débitrices",
            measures = "Les familles classées par encours décroissant, avec leurs enfants actifs et leur pire retard.",
            calc = "Encours INV-4 par famille ; « pire retard » = max des jours de retard de leurs lignes."
        ),
        "pct" to Entry(
            title = "Concentration (%)",
            measures = "La part de l'encours total détenue par le top N.",
            calc = "round(Σ top N ÷ encours total × 100) — 100 % signifierait que tout l'encours tient dans le top N."
        ),
        "worst" to Entry(
            title = "Pire retard (jours)",
            measures = "L'ancienneté du retard le plus ancien de la famille.",
            calc = "max(floor((maintenant − échéance) ÷ jour)) sur les lignes non soldées de la famille."
        ),
    )

    /** The dynamics section. */
    val dynamics: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Dynamique des effectifs et fratries",
            measures = "La structure de la population scolaire : indice de fratries, tailles de familles, équilibre des sections.",
            calc = "Depuis les élèves ACTIFS : familles = parents distincts d'élèves actifs ; aucune plafond de capacité n'intervient (directive propriétaire)."
        ),
        "sibling" to Entry(
            title = "Indice de fratries",
            measures = "Le multiplicateur familial : combien d'élèves scolarisés par famille en moyenne.",
            calc = "élèves actifs ÷ familles (2 décimales) ; > 1 = les familles inscrivent plusieurs enfants (fidélité)."
        ),
        "sizes" to Entry(
            title = "Tailles de familles",
            measures = "La distribution des familles par nombre d'enfains actifs.",
            calc = "Comptage par taille (1, 2, 3, 4, 5+) sur les élèves actifs."
        ),
        "multiChild" to Entry(
            title = "Familles multi-enfants",
            measures = "Les familles avec ≥ 2 enfants actifs — le cœur de la fidélité (et du risque de concentration).",
            calc = "Comptage + part de l'ensemble des familles avec enfant actif."
        ),
        "imbalance" to Entry(
            title = "Déséquilibre des sections",
            measures = "Les niveaux dont les sections parallèles dérivent en effectif.",
            calc = "Par niveau à ≥ 2 sections : écart max−min ; signalé si écart ≥ 10 ou max ≥ 1,5 × min (aucun plafond artificiel).",
            status = "Signalé = candidat à une redistribution (pas une erreur de données)."
        ),
    )

    /** The transport section. */
    val transport: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Rendement transport",
            measures = "Le recouvrement par ligne de transport (destination normalisée) et le taux de remplissage.",
            calc = "Les lignes transport sont attribuées aux lignes de transport par le parent de l'élève inscrit ; les villes sont normalisées (TOWN_ALIASES)."
        ),
        "riders" to Entry(
            title = "Élèves transportés",
            measures = "Les élèves actifs avec une affectation de transport (et ceux sans).",
            calc = "Comptage des transportTier non nuls normalisés ; « non résolues » = les orthographes de villes inconnues, listées telles quelles."
        ),
        "routes" to Entry(
            title = "Lignes par destination",
            measures = "Les effectifs et la santé financière par ligne (facturé, encaissé, reste dû).",
            calc = "Σ sur les lignes de transport des familles conductrices de la ligne ; taux = round(encaissé ÷ facturé × 100)."
        ),
        "unresolved" to Entry(
            title = "Valeurs non résolues",
            measures = "Les orthographes de villes non reconnues par le tableau d'alias (données sources à corriger).",
            calc = "Comptage verbatim des transportTier non vides normalisés en « autres » — honnête, jamais silencieux."
        ),
    )

    /** The services section. */
    val services: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Revenus services spécialisés",
            measures = "Les encaissements des services hors scolarité/transport : psychologie, orthophonie, activités, cantine, uniforme, livres, tablier, autres.",
            calc = "Depuis les PAIEMENTS réglés de la période (catégorie du paiement) — la même base que la KPI « Encaissé services » ; les catégories sans activité sont omises (état vide honnête)."
        ),
        "revenue" to Entry(
            title = "Revenu par service",
            measures = "Le total encaissé par catégorie de service sur la période filtrée.",
            calc = "Σ payment.amount des paiements réglés de la catégorie (statut « réglé » uniquement)."
        ),
        "volume" to Entry(
            title = "Volume par service",
            measures = "Le nombre d'opérations et d'élèves distincts servis par catégorie.",
            calc = "Comptage des paiements + ensemble des studentId portés (quand le paiement est attribué à un élève)."
        ),
    )

    /** The risk section. */
    val risk: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Radar de vigilance multi-critères",
            measures = "La synthèse du triple risque : pédagogique, assiduité, financier — et les élèves en tension sur les trois axes.",
            calc = "Une seule évaluation des profils (evaluateStudentRiskProfiles) : moyenne < 10, absences ≥ 3, dette ouverte ; le compteur triple = les trois à la fois.",
            status = "Chaque compteur est cliquable vers la console pour la liste nominative."
        ),
        "academic" to Entry(
            title = "Moyenne < 10",
            measures = "Les élèves sous la moyenne de passage (10/20).",
            calc = "Comptage des profils à GPA < 10 (GPA null = pas de notes : exclu, pas compté à zéro)."
        ),
        "attendance" to Entry(
            title = "Absences ≥ 3",
            measures = "Les élèves avec au moins 3 absences enregistrées (justifiées ou non).",
            calc = "Comptage des profils à absences ≥ 3 sur la période du filtre."
        ),
        "financial" to Entry(
            title = "Dette ouverte",
            measures = "Les élèves dont la famille a un encours ouvert.",
            calc = "Comptage des profils avec encours familial > 0 (INV-4, toutes années)."
        ),
    )

    /** The methodMix section. */
    val methodMix: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Mix des méthodes de paiement",
            measures = "La répartition des encaissements par méthode (espèces, chèque, virement).",
            calc = "Σ des montants de la tranche filtrée groupée par payments.method ; parts = round(montant ÷ total × 100)."
        ),
        "donut" to Entry(
            title = "Anneau des méthodes",
            measures = "La part visuelle de chaque méthode ; le centre porte le total.",
            calc = "Les parts sont les mêmes que la liste classée ci-contre (même dérivation)."
        ),
    )

    /** The categoryMix section. */
    val categoryMix: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Mix des catégories de paiement",
            measures = "Le classement des catégories de facturation par montant encaissé.",
            calc = "Σ par payments.category sur la tranche filtrée ; « Autres (N) » fusionne la queue au-delà du top 6."
        ),
        "toggle" to Entry(
            title = "Bascule Montant / Opérations",
            measures = "Le classement par montant total ou par nombre d'opérations.",
            calc = "Même dérivation, clé de tri différente (Σ montants vs comptage)."
        ),
    )

    /** The yoy section. */
    val yoy: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Comparaison annuelle des revenus",
            measures = "L'évolution encaissements de cette année scolaire vs la précédente, mois par mois.",
            calc = "Séries du référentiel (revenueForRange) alignées par étiquette de mois ; l'année précédente est chargée pour la même fenêtre décalée d'un an.",
            status = "Δ% = round((actuel − précédent) ÷ précédent × 100) ; nul quand le précédent vaut 0 (pas de tendance sans base)."
        ),
        "delta" to Entry(
            title = "Δ% mensuel",
            measures = "L'écart relatif du mois entre les deux années.",
            calc = "Par mois : round((actuel − précédent) ÷ précédent × 100) — affiché seulement si le précédent > 0."
        ),
        "totals" to Entry(
            title = "Totaux comparés",
            measures = "Les cumuls des deux années et l'écart global.",
            calc = "Σ des mois affichés par année ; Δ global = round((Σ actuel − Σ précédent) ÷ Σ précédent × 100)."
        ),
    )

    /** The aging section. */
    val aging: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Vieillissement des créances",
            measures = "La répartition de l'encours non recouvré par ancienneté (0–30, 31–60, 61–90, 91–180, > 180 jours).",
            calc = "Buckets du référentiel (debtByAagingForRange) : chaque tranche non soldée alimente le bucket de ses jours de retard ; la barre empilée = 100 % de l'encours.",
            status = "Plus la bande est à droite, plus le recouvrement devient improbable — > 180 j = dette quasi-perdue."
        ),
        "b0_30" to Entry(
            title = "0–30 jours",
            measures = "L'encours en retard de moins d'un mois.",
            calc = "Reste dû INV-4 des lignes à 1–30 jours de retard — la couche la plus récupérable."
        ),
        "b31_60" to Entry(
            title = "31–60 jours",
            measures = "L'encours en retard d'un à deux mois.",
            calc = "Reste dû des lignes à 31–60 jours de retard — relance active."
        ),
        "b61_90" to Entry(
            title = "61–90 jours",
            measures = "L'encours en retard de deux à trois mois.",
            calc = "Reste dû des lignes à 61–90 jours de retard."
        ),
        "b91_180" to Entry(
            title = "91–180 jours",
            measures = "L'encours en retard de trois à six mois.",
            calc = "Reste dû des lignes à 91–180 jours de retard — pression forte requise."
        ),
        "b180plus" to Entry(
            title = "> 180 jours",
            measures = "L'encours au-delà de six mois de retard.",
            calc = "Reste dû des lignes à plus de 180 jours — candidat provision/perte ; alimente le verrouillage des comptes délinquants (> 90 j, Finances → Créances)."
        ),
    )

    /** The pareto section. */
    val pareto: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Pareto des familles débitrices",
            measures = "Le 80/20 : la part de l'encours total portée par les plus grosses familles (toutes années).",
            calc = "Sommaires de dettes du référentiel, classés par encours décroissant ; la courbe cumulée = round(cumulé ÷ total affiché × 100).",
            status = "Si ~20 % des familles portent ~80 % de l'encours, prioriser le recouvrement sur la tête."
        ),
        "cum" to Entry(
            title = "Courbe cumulée (%)",
            measures = "La part cumulative de l'encours captée en descendant le classement.",
            calc = "Σ glissante des encours ÷ Σ des familles affichées × 100."
        ),
    )

    /** The payroll section. */
    val payroll: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Coûts du Personnel — Réalisé vs Projeté",
            measures = "La trajectoire des coûts salariaux (réalisé vs projection) et le besoin de financement mensuel.",
            calc = "La projection canonique (computePayrollForecast, ADR-024) — la MÊME que les pages Personnel et Finances : salaires actifs + charges, au fil des mois.",
            status = "Besoin de financement = l'écart entre la projection et l'encaissement attendu du mois."
        ),
        "realized" to Entry(
            title = "Réalisé",
            measures = "Les coûts salariaux effectivement payés (historique).",
            calc = "Σ des paiements de salaires enregistrés (salaryPayments) par période."
        ),
        "funding" to Entry(
            title = "Besoin de financement",
            measures = "Le trou de trésorerie projeté du mois (coûts projetés vs recettes attendues).",
            calc = "Projection du mois − encaissements attendus (la même convention que Finances)."
        ),
    )

    /** The console section. */
    val console: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Console de requêtes opérationnelles",
            measures = "L'exploration nominative des profils de risque : recherche, filtres, tri, et la fiche 360° par élève.",
            calc = "Les profils sont l'objet partagé unique évalué une fois (jamais recalculé) ; la console filtre/trie ce même objet.",
            status = "Les compteurs de la barre de stats reflètent le filtre courant (dossiers, créances cumulées, moyenne)."
        ),
        "stats" to Entry(
            title = "Barre de statistiques de la console",
            measures = "Dossiers filtrés, créances cumulées de la sélection, moyenne de cohorte.",
            calc = "Comptage/Σ sur les profils filtrés — mêmes définitions que le radar multi-critères."
        ),
    )

    /** The crossRisk section. */
    val crossRisk: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Matrice croisée de risque",
            measures = "Le croisement des axes de risque (pédagogie × assiduité, pédagogie × finances, etc.).",
            calc = "Croisement des indicateurs binaires du profil partagé (mêmes seuils que le radar)."
        ),
    )

    /** The pivot section. */
    val pivot: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Matrice pivot pédagogique",
            measures = "La distribution des moyennes par classe/niveau (le pivot pédagogique).",
            calc = "Agrégation des GPA des profils partagés par classe ; les classes vides sont omises (état vide honnête)."
        ),
    )

    /** The inspector section. */
    val inspector: Map<String, Entry> = mapOf(
        "card" to Entry(
            title = "Inspection directe / provenance des données",
            measures = "Le lien vers l'inspecteur de données : la traçabilité de chaque chiffre jusqu'aux lignes sources.",
            calc = "Chaque déclencheur porte le domaine, la métrique et la valeur source ; l'inspecteur résout la lignée (T-389) — lignes, définition, écarts.",
            status = "« Écart » dans l'inspecteur = un écart de données réel, jamais définitionnel (les deux côtés partagent la dérivation)."
        ),
    )

    /** The flat dotted-key lookup ("waveVelocity.collectedPct" — the
     *  desktop's t() key convention; null when the key misses — the
     *  InfoTip renders NOTHING rather than a fabricated text). */
    fun tip(key: String): Entry? {
        val dot = key.indexOf('.')
        if (dot <= 0 || dot == key.length - 1) return null
        val section = key.substring(0, dot)
        val entry = key.substring(dot + 1)
        val map = when (section) {
            "viewMode" -> viewMode
            "header" -> header
            "slicers" -> slicers
            "statStrip" -> statStrip
            "waveVelocity" -> waveVelocity
            "triage" -> triage
            "erosion" -> erosion
            "concentration" -> concentration
            "dynamics" -> dynamics
            "transport" -> transport
            "services" -> services
            "risk" -> risk
            "methodMix" -> methodMix
            "categoryMix" -> categoryMix
            "yoy" -> yoy
            "aging" -> aging
            "pareto" -> pareto
            "payroll" -> payroll
            "console" -> console
            "crossRisk" -> crossRisk
            "pivot" -> pivot
            "inspector" -> inspector
            else -> return null
        }
        return map[entry]
    }
}
