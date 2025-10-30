
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
BitPacking - démonstration

--- CROSS ---
n=10000, k=15
compression : total 1379600 ns, moyenne 275,92 µs
get de 1000 valeurs aléatoires : total 1932500 ns, moyenne 0,39 ms par itération
décompression : total 2494200 ns, moyenne 498,84 µs
Vérification : décompression == original : true

--- NOCROSS ---
n=10000, k=15
compression : total 446200 ns, moyenne 89,24 µs
get de 1000 valeurs aléatoires : total 2660800 ns, moyenne 0,53 ms par itération
décompression : total 1667100 ns, moyenne 333,42 µs
Vérification : décompression == original : true

--- OVERFLOW ---
n=10000, petitK=6, bitsChamp=7, nbDebordement=20
compression : total 5456400 ns, moyenne 1091,28 µs
get de 1000 valeurs aléatoires : total 699200 ns, moyenne 0,14 ms par itération      
décompression : total 3670300 ns, moyenne 734,06 µs
Vérification : décompression == original : true

Exemples get(i) :
i=0 original=1024 cross.get=1024 nocross.get=1024 overflow.get=1024
i=1 original=0 cross.get=0 nocross.get=0 overflow.get=0
i=2 original=34 cross.get=34 nocross.get=34 overflow.get=34
i=3 original=29 cross.get=29 nocross.get=29 overflow.get=29
i=4 original=45 cross.get=45 nocross.get=45 overflow.get=45
i=500 original=2048 cross.get=2048 nocross.get=2048 overflow.get=2048
i=501 original=46 cross.get=46 nocross.get=46 overflow.get=46
i=502 original=10 cross.get=10 nocross.get=10 overflow.get=10
i=503 original=10 cross.get=10 nocross.get=10 overflow.get=10
i=504 original=19 cross.get=19 nocross.get=19 overflow.get=19

```
---

## Auteur

| Nom | Prénom |
|------|---------|
| Giacchero | Baptiste | 

---
