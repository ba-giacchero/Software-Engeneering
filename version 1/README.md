
## Fonctionnalités principales

- **Compression / Décompression** d’un tableau d’entiers positifs.  
- **Accès direct** à un élément compressé via `get(i)`.  
- **Trois modes de compression :**
  1. `CrossBitPacker` → compression avec chevauchement possible entre deux entiers de 32 bits.  
  2. `NoCrossBitPacker` → compression sans chevauchement, plus simple et rapide.  
  3. `OverflowBitPacker` → compression avancée avec **zone de débordement** pour les valeurs nécessitant plus de bits.  
- **Factory Pattern** (`BitPackerFactory`) pour choisir dynamiquement le type de compresseur.  
- **Benchmarks intégrés** pour mesurer la vitesse de compression, décompression et lecture (`get()`).



## Compilation et exécution

### Prérequis
- **Java 17+** (ou version ultérieure)
- Un terminal ou IDE (IntelliJ, Eclipse, VSCode…)

### Compilation

```bash
javac BitPackingExample.java
```

### Exécution

```bash
java BitPackingExample
```

Le programme génère automatiquement un tableau de 10 000 entiers aléatoires et exécute :
- La compression avec chaque algorithme (`CROSS`, `NOCROSS`, `OVERFLOW`)
- Les tests de performance (temps de compression, décompression, accès)
- La vérification d’exactitude (comparaison avant/après compression)

---

##  Exemple de sortie console

```
BitPackingExample - démonstration

--- CROSS (chevauchement autorisé) ---
n=10000, k=12
compression : total 120000 ns, moyenne 24.00 µs
get (1000 appels aléatoires) : moyenne 0.50 ms
décompression : moyenne 18.20 µs
Vérification : décompression == original : true

--- NOCROSS (pas de split entre mots) ---
n=10000, k=12
compression : total 95000 ns, moyenne 19.00 µs
get (1000 appels aléatoires) : moyenne 0.47 ms
décompression : moyenne 16.80 µs
Vérification : décompression == original : true

--- OVERFLOW (zone de débordement) ---
n=10000, smallK=5, fieldBits=7, overflowCount=200
compression : total 150000 ns, moyenne 30.00 µs
get (1000 appels aléatoires) : moyenne 0.51 ms
décompression : moyenne 20.00 µs
Vérification : décompression == original : true
```

---

## Détails techniques

- **Représentation binaire** : chaque entier est stocké sur *k* bits déterminés automatiquement.
- **Compression bit à bit** : utilisation d’opérations de masquage (`maskLow(k)`) et de décalage (`<<`, `>>>`) pour empaqueter les bits.
- **Overflow** : une valeur nécessitant plus de bits que `smallK` est placée dans une zone de débordement référencée par un index compacté.
- **Benchmark** : utilisation de `System.nanoTime()` pour mesurer les temps avec plusieurs itérations et warm-up pour stabiliser la JVM.

---

## Tests et vérification

- La méthode `main()` contient un protocole de test complet :
  - Génération des données
  - Compression/Décompression
  - Accès aléatoire (`get()`)
  - Vérification de la fidélité (`Arrays.equals`)
- Les temps d’exécution moyens sont affichés dans la console.

---

## 🚀 Extensions possibles

- Implémentation **multi-thread** pour la compression de grands tableaux.  
- Adaptation automatique du mode de compression selon le contenu.  
- Intégration réseau (simulation de transmission avec latence).  
- Ajout d’un export JSON ou binaire pour l’envoi sur le web.  

---

## Auteur

| Nom | Prénom |
|------|---------|
| Giacchero | Baptiste | 

---